# Dashboard Builder Engine

低代码看板的编排与发布引擎：保存用户拖拽编排出的组件树，经校验后生成可渲染的页面定义，
并支持草稿/发布隔离、并发编辑冲突检测、引用校验、原子发布与精确回滚、角色权限控制。

## 运行与测试

```bash
mvn spring-boot:run   # 启动服务（默认端口见 application.properties）
mvn test              # 运行全部自动化测试
```

## 核心概念

### 组件树（ComponentNode）

```json
{
  "id": "chart-1",
  "type": "chart",
  "props": { "title": "销售额" },
  "dependencies": ["filter-1"],
  "dataSourceId": "ds-sales",
  "children": []
}
```

- `children` 支持任意层级嵌套；删除父组件时其整个子树一并删除，不会产生悬空子节点。
- `dependencies` 声明对其他组件 id 的引用（联动场景）；`dataSourceId` 绑定已注册数据源。
- 内置数据源：`ds-sales`、`ds-traffic`、`ds-user`（`DataSourceRegistry` 可扩展注册）。

### 版本语义

| 概念 | 语义 |
| --- | --- |
| `draftVersion` | 草稿版本号，从 0 开始，每次成功编辑单调递增。编辑操作必须携带 `baseVersion`，与当前草稿版本一致才会被接受（乐观并发控制）。 |
| `publishedVersion` | 发布版本号，从 1 开始全局单调递增，**只增不改**。每个版本是发布时刻草稿的不可变深拷贝快照。 |
| 当前线上版本 | 指向某个已发布版本的指针。渲染接口只读取该指针指向的快照，永远看不到未发布的草稿改动。 |
| 回滚 | 不修改历史，而是以目标历史版本的内容**追加一个新版本**并置为当前版本。因此回滚后渲染结果与目标版本当时完全一致，且历史记录保持只增。 |

### 并发与原子性保证

- 同一看板的所有编辑/发布操作在服务端串行化；`baseVersion` 过期的操作返回 `409`，
  错误信息说明期望版本与当前版本，**绝不静默覆盖**。
- 发布流程为「校验 → 构造不可变快照 → 原子切换指针」。任一步失败都抛出异常，
  线上版本保持原状，不存在中间状态。
- 越权请求在任何状态变更之前被拒绝。

## 接口约定

所有接口通过请求头 `X-Role` 传递角色：`VIEWER`（默认）/ `EDITOR` / `ADMIN`。

| 角色 | 读已发布 | 读草稿 | 编辑草稿 | 发布 / 回滚 |
| --- | --- | --- | --- | --- |
| VIEWER | ✓ | ✗ | ✗ | ✗ |
| EDITOR | ✓ | ✓ | ✓ | ✗ |
| ADMIN | ✓ | ✓ | ✓ | ✓ |

### 接口列表

| 方法 | 路径 | 所需角色 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/dashboards/{id}/draft` | EDITOR+ | 读取草稿，返回 `{draftVersion, root}` |
| POST | `/api/dashboards/{id}/draft/operations` | EDITOR+ | 应用一次编辑操作，返回新 `draftVersion` |
| POST | `/api/dashboards/{id}/draft/validate` | EDITOR+ | 预校验草稿，返回 `{valid, errors[]}` |
| GET | `/api/dashboards/{id}/published` | 任意 | 读取当前线上渲染内容 |
| GET | `/api/dashboards/{id}/published/history` | 任意 | 发布历史列表 |
| POST | `/api/dashboards/{id}/publish` | ADMIN | 校验并发布当前草稿 |
| POST | `/api/dashboards/{id}/rollback/{version}` | ADMIN | 回滚到指定历史版本 |

### 编辑操作（EditOperation）

```json
{ "type": "ADD|UPDATE|REMOVE|MOVE", "baseVersion": 3,
  "componentId": "c1", "parentId": "row-1", "index": 0,
  "node": { "id": "c1", "type": "chart", "props": {}, "dependencies": [], "dataSourceId": "ds-sales" } }
```

- `ADD`：`node` 挂到 `parentId`（缺省为根）的 `index` 位置（缺省追加）。
- `UPDATE`：以 `node` 的内容覆盖目标组件的 `type/props/dependencies/dataSourceId`（不影响其子树）。
- `REMOVE`：删除目标组件及其整个子树。
- `MOVE`：移动组件到新父节点；禁止移入自己的子树。

### 错误约定

| 状态码 | 场景 | 响应体 |
| --- | --- | --- |
| 403 | 角色越权 / 未知角色 | `{error, message}`，无任何状态变更 |
| 404 | 看板未发布 / 组件不存在 / 历史版本不存在 | `{error, message}` |
| 409 | `baseVersion` 过期、组件 id 冲突、非法移动 | `{error, message}`，message 说明冲突原因 |
| 422 | 发布校验失败 | `{error, message, errors:[{componentId, message}]}`，每条错误可定位到组件 |

校验规则：组件 id 非空且唯一；`dependencies` 引用的组件必须存在；
`dataSourceId` 必须是已注册数据源；组件依赖图中不允许循环引用（错误信息包含完整环路径）。

## 测试覆盖

- `EngineServiceTest`：增删改与嵌套、父删子树级联、草稿/发布隔离、顺序与真实多线程并发冲突、
  缺失/循环引用校验、发布失败原子性、回滚一致性与历史只增、各角色越权且无状态变更。
- `ApiLayerTest`：HTTP 层角色头约定、状态码（403/404/409/422）与错误体结构。
