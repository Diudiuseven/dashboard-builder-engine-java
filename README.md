# Dashboard Builder Engine

低代码看板的编排与发布引擎：保存拖拽编排出来的组件树，校验引用关系，并发布成一份
**不可变、可渲染**的页面定义。Spring Boot 3 / Java 17，内存存储（JSON 深拷贝隔离）。

## 1. 核心概念与版本语义

| 概念 | 含义 |
| --- | --- |
| `Dashboard` | 看板聚合：**一份草稿** + **一条只增不改的已发布版本历史**。 |
| 草稿 `draft` | 可任意编辑的工作区，包含组件树、数据源，以及乐观锁字段 `editVersion`。 |
| `editVersion` | 草稿乐观锁版本，**每次成功的草稿修改 +1**，初始为 0。所有写请求必须带 `expectedEditVersion`，与当前值不一致即返回 `409 CONFLICT`，绝不静默覆盖。 |
| 已发布版本 `publishedVersion` | 发布时从草稿**深拷贝**生成的不可变快照，版本号单调递增（1, 2, 3 …）。历史最后一个版本即**线上版本**。 |
| 回滚 | 回滚**不重写历史**，而是把目标历史版本的内容深拷贝后追加为一个新的已发布版本（记录 `restoredFromVersion`）。因此回滚后渲染结果与目标版本逐字段一致，且草稿不受影响。 |

### 草稿 / 已发布隔离

* 读取已发布内容（`/published`、`/published/{version}`、`/versions`）永远只看快照，
  看不到任何未发布的草稿改动；正在渲染的版本也不受后续草稿编辑影响。
* 仓储的每次 save/find 都经过 JSON 往返深拷贝，调用方拿到的对象与存储状态完全隔离，
  即使误改返回对象也不会污染草稿或线上快照。

### 发布的原子性

发布在**每个看板的锁内**执行，顺序为：

1. 校验乐观锁版本（并发发布/编辑冲突 → `409`，无状态变更）；
2. 全量校验草稿；不通过 → `422`，无状态变更；
3. 在局部变量上构建完整快照（含模拟的下游失败开关 `failAfterSnapshot`）；
4. 只有快照构建成功，才把快照**一次性追加**进历史并保存。

任何一步失败都发生在历史被触碰之前，线上版本不可能停留在中间状态。
失败后草稿保持原样，可修复后重新发布。

## 2. 组件树编排

* 组件为嵌套树：`ComponentNode{ id, type, name, props, dataBindings, componentRefs, children }`。
* 新增可指定 `parentId`（为空则挂到根）；父节点不存在将被拒绝。
* 删除父组件会**连带整个子树一起摘除**，子节点从树上断开，不可能产生"挂空"的可达节点。
* 若子树**被子树外的组件引用**，直接删除会造成悬空引用：
  * 默认拒绝并返回每个引用点的定位错误（`422`）；
  * `force=true` 时删除子树并清除这些入引用，再做保存。
* 新增时校验 id 非空/不重复、type 非空、父节点存在；**引用完整性在发布闸门统一校验**，
  以允许先放置被引用方再补引用这类正常编辑顺序。

## 3. 引用校验

`DraftValidator` 在发布时（或随时 `GET .../validate`）检查：

| 错误码 | 含义 |
| --- | --- |
| `STRUCTURAL_ERROR` | 空 id/type、重复组件 id、data source key 与 id 不符等 |
| `MISSING_COMPONENT_REFERENCE` | `componentRefs` 指向不存在的组件（自引用算环，不算缺失） |
| `MISSING_DATA_SOURCE_REFERENCE` | `dataBindings` 指向不存在的数据源 |
| `CIRCULAR_REFERENCE` | 组件引用有向图存在环（含自引用），基于染色 DFS 检测 |

每个 issue 形如：

```json
{ "code": "MISSING_COMPONENT_REFERENCE",
  "path": "components[0].componentRefs.filter",
  "message": "Referenced component does not exist: ghost" }
```

`path` 是 JSON 风格定位串，嵌套子节点形如 `components[0].children[1].dataBindings.series`。

## 4. 鉴权约定

所有 `/api/**` 请求必须带头 `X-User-Role: VIEWER | EDITOR | PUBLISHER`，缺失或无法识别
一律 `403 FORBIDDEN`。可选头 `X-User-Name` 仅用于审计字段。角色等级：

```
VIEWER < EDITOR < PUBLISHER
```

| 操作 | 最低角色 |
| --- | --- |
| 读取草稿 / 已发布 / 历史 / 校验结果 | VIEWER |
| 创建看板、组件增删改、数据源增改 | EDITOR |
| 发布、回滚 | PUBLISHER |

鉴权在拦截器中、进入控制器**之前**完成，因此越权请求不会执行任何业务逻辑、不产生任何
状态变更。

## 5. HTTP 接口约定

基础路径 `/api/dashboards`。写操作的 `expectedEditVersion` 均为草稿乐观锁版本。

| 方法 | 路径 | 角色 | 说明 |
| --- | --- | --- | --- |
| POST | `/` | EDITOR | 创建看板，body `{ "name": "..." }` |
| GET | `/{id}` | VIEWER | 返回 `{ dashboard, publishedVersion }` |
| POST | `/{id}/components` | EDITOR | 新增组件，body `{ component, parentId, expectedEditVersion }` |
| PUT | `/{id}/components/{componentId}` | EDITOR | 局部更新组件（非 null 字段才覆盖），body 含 `expectedEditVersion` |
| DELETE | `/{id}/components/{componentId}` | EDITOR | 删除整棵子树，body `{ expectedEditVersion, force }` |
| POST | `/{id}/data-sources` | EDITOR | 新增数据源，body `{ dataSource, expectedEditVersion }` |
| GET | `/{id}/validate` | VIEWER | 返回当前草稿的全部校验 issue（空数组表示通过） |
| POST | `/{id}/publish` | PUBLISHER | 发布草稿，body `{ expectedEditVersion, failAfterSnapshot }` |
| GET | `/{id}/published` | VIEWER | 当前线上版本 |
| GET | `/{id}/published/{version}` | VIEWER | 任意历史版本 |
| GET | `/{id}/versions` | VIEWER | 已发布版本历史（只增不改） |
| POST | `/{id}/rollback` | PUBLISHER | 回滚，body `{ "version": 1 }`，追加新版本 |

### 状态码与错误体

* `200` 成功；
* `400 BAD_REQUEST`：JSON 非法 / 参数不合法；
* `403 FORBIDDEN`：缺少/非法角色或越权；
* `404 NOT_FOUND`：看板、组件、历史版本不存在；
* `409 CONFLICT`：乐观锁冲突，body 含 `currentEditVersion` 供客户端重载后重试：

```json
{ "error": "CONFLICT",
  "message": "Draft editVersion 0 is stale; current version is 1",
  "currentEditVersion": 1 }
```

* `422 UNPROCESSABLE_ENTITY`：校验失败，body 为 `{ error, message, issues: [...] }`；
* `500 PUBLISH_FAILED`：快照构建后的发布步骤失败（线上版本保持不变）。

成功的草稿修改统一返回：

```json
{ "status": "OK", "dashboardId": "...", "editVersion": 2, "publishedVersion": null }
```

发布/回滚成功返回 `status=PUBLISHED` 与新的 `publishedVersion`。

### 调用示例

```bash
# 编辑者创建看板并加一个组件
curl -s -X POST localhost:8080/api/dashboards \
  -H 'X-User-Role: EDITOR' -H 'Content-Type: application/json' \
  -d '{"name":"Sales Board"}'

curl -s -X POST localhost:8080/api/dashboards/$ID/components \
  -H 'X-User-Role: EDITOR' -H 'Content-Type: application/json' \
  -d '{"component":{"id":"c1","type":"text"},"parentId":null,"expectedEditVersion":0}'

# 发布
curl -s -X POST localhost:8080/api/dashboards/$ID/publish \
  -H 'X-User-Role: PUBLISHER' -H 'Content-Type: application/json' \
  -d '{"expectedEditVersion":1,"failAfterSnapshot":false}'

# 回滚到版本 1（实际追加出版本号更大的新快照）
curl -s -X POST localhost:8080/api/dashboards/$ID/rollback \
  -H 'X-User-Role: PUBLISHER' -H 'Content-Type: application/json' \
  -d '{"version":1}'
```

## 6. 测试

`mvn test`，共 23 个用例，覆盖：

* `DraftValidatorTest`：缺失组件/数据源引用、循环引用（含自引用）、结构错误与定位路径；
* `DashboardServiceTest`：父组件连子树删除、悬空引用拒绝/强制清理、草稿-发布隔离、
  无效草稿发布被拒；
* `DashboardConcurrencyAndPublishTest`：
  * 两名编辑者基于同一 `editVersion` 并发提交，**恰好一个成功、一个 409**，无丢失更新；
  * 多编辑者冲突后重载版本号重试，最终全部成功且结构完整；
  * 发布在快照构建后失败时线上版本保持原版本、历史不增长、草稿可重新发布；
  * 回滚后内容与历史版本逐字段一致、历史只增不改、草稿不受影响；回滚到不存在版本被拒；
* `DashboardApiTest`（MockMvc 端到端）：无角色头/越权（VIEWER 写、EDITOR 发布）被拒且无
  状态变更、`409` 携带当前版本、`422` 返回可定位 issue、发布失败与草稿隔离的 HTTP 行为。

## 7. 设计说明与局限

* 存储为内存实现（`InMemoryDashboardRepository`），重启数据丢失；`DashboardRepository`
  接口可替换为数据库实现（需保留"读/写均深拷贝"的隔离语义）。
* 每个看板一把锁 + 乐观锁版本：锁保证进程内原子性，乐观锁给调用方提供可解释的冲突结果；
  多实例部署时需把锁与版本检查下沉到数据库事务（如 `WHERE edit_version = ?` 条件更新）。
* 发布快照即渲染定义：渲染端直接消费 `/published` 返回的组件树与数据源映射。
