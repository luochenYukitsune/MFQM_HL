# 实施与验收

1. 给 `SinkingMotionTest`、`CoatingVoxelsTest` 和新 UV 裁切脚本添加失败检查，分别证明旧等级提前、过浓和凸起过厚的问题。
2. 生成器 `tools/coating_height.py` 仅新增五组十级高度蒙版，保留原材质细节，明确顶面/底面和侧面高度；添加独立 Python 测试及输出哈希验证。
3. 改 `SinkingMotion.coatingLevel` 的取整、`QuicksandPhysics.updateCoating` 的参考高度、`MuddyPlayerLayer.Coating.texture` 的蒙版来源和 `CoatingVoxels` 的透明度/厚度。
4. 加强实际服务器与客户端覆盖夹具，检查新鲜腿部、躯干/头部推进、持久残留及宽/细臂；更新中文说明和版本 0.6.5-dev。
5. 构建与资源验证、服务器回归、实际 JAR 合载覆盖测试；直接查看必要截图，审查差异，记录证据并提交本地 Git。
