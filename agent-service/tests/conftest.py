import pytest

from agent_service import planner


@pytest.fixture(autouse=True)
def isolated_planner(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(planner, "deepseek_planner", None)
    planner._record_diagnostics("none", "none")
