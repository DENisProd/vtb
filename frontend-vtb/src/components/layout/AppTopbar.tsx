import { Button, Chip, Input, Tooltip } from "@heroui/react";
import {
  ArrowUpTrayIcon,
  BoltIcon,
  MagnifyingGlassIcon,
  PlayCircleIcon,
} from "@heroicons/react/24/outline";

import { ThemeSwitch } from "@/components/theme/ThemeSwitch";
import { useTestFlowStore } from "@/stores/testflow-store";

export const AppTopbar = () => {
  const artifacts = useTestFlowStore((state) => state.artifacts);
  const scenarios = useTestFlowStore((state) => state.scenarios);
  const executions = useTestFlowStore((state) => state.runnerExecutions);
  const setSelectedExecution = useTestFlowStore(
    (state) => state.setSelectedExecution,
  );
  const issuesCount = useTestFlowStore((state) => state.analysisIssues.length);

  const activeRun =
    executions.find(
      (run) => run.status === "running" || run.status === "queued",
    ) ?? executions[0];

  return (
    <header className="border-b border-white/10 bg-[var(--app-bg-alt)]/80 backdrop-blur-xl">
      <div className="flex flex-col gap-3 px-4 py-3 md:px-8">
        <div className="flex items-center gap-4">

          <div className="ml-auto flex items-center gap-2">
            <ThemeSwitch />
          </div>
        </div>
      </div>
    </header>
  );
};

const TopbarStat = ({
  label,
  value,
  hint,
  onClick,
}: {
  label: string;
  value: number | string;
  hint?: string;
  onClick?: () => void;
}) => (
  <button
    className="app-card px-4 py-3 text-left transition-all hover:border-[var(--app-primary)]"
    type="button"
    onClick={onClick}
  >
    <div className="text-xs uppercase tracking-wide text-muted">
      {label}
    </div>
    <div className="text-2xl font-semibold text-[var(--app-text)]">
      {value}
    </div>
    {hint && <div className="text-xs text-muted">{hint}</div>}
  </button>
);

