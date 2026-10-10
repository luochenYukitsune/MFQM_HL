"""Install UTF-8 adhesive controls and hints without depending on shell pipe encoding."""
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1] / "src/main/resources/assets/mfqm/lang"
patches = {
    "zh_cn": {"key.mfqm.struggle": "挣扎", "key.mfqm.reel_in": "收绳（需手持）", "key.mfqm.release": "断开连接（需手持）",
        "key.categories.mfqm.controls": "MFQM 操作", "tooltip.mfqm.glue": "挣扎削弱黏连，也会加深下陷；停止挣扎可保持深度。",
        "tooltip.mfqm.sticky_board": "底板可回收重复使用；用胶水桶补胶，挣扎会消耗涂层。"},
    "en_us": {},
    "ru_ru": {"key.mfqm.struggle": "Вырываться", "block.mfqm.glue": "Клей", "fluid.mfqm.glue": "Клей", "item.mfqm.glue_bucket": "Ведро клея",
        "tooltip.mfqm.glue": "Борьба ослабляет связи, но заставляет погружаться. Остановитесь, чтобы удержать глубину.",
        "tooltip.mfqm.sticky_board": "Многоразовая доска. Нанесите клей ведром; борьба истощает покрытие."},
}
for locale, patch in patches.items():
    path = root / f"{locale}.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    data.update(patch)
    data.pop("message.mfqm.trapped", None)  # Automatic trapped-key hints were retired.
    if locale == "zh_cn":
        hints = ["生存模式手持使用", "对目标或支撑物右键挂接", "绳索也能够取留存靴子", "操作", "右键", "发射并挂接",
                 "挂接后按住右键", "收绳", "潜行并按住右键", "放绳；X 断开"]
        for prefix in ("itemRope", "itemGrapplingHook"):
            data.update({f"{prefix}.instruction{i}": hint for i, hint in enumerate(hints, 1)})
    elif locale == "ru_ru":
        for prefix in ("itemRope", "itemGrapplingHook"):
            data.update({f"{prefix}.instruction7": "Удерживать ПКМ", f"{prefix}.instruction8": "натянуть верёвку",
                         f"{prefix}.instruction9": "Приседание + ПКМ", f"{prefix}.instruction10": "удлинить; X — отсоединить"})
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
