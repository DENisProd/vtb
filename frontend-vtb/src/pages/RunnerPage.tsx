import { useEffect, useMemo, useState } from "react";
import {
  Button,
  Card,
  CardBody,
  CardHeader,
  Chip,
  Input,
} from "@heroui/react";
import {
  BoltIcon,
  CloudArrowDownIcon,
  PauseCircleIcon,
  PlayCircleIcon,
} from "@heroicons/react/24/outline";

import { useTestFlowStore } from "@/stores/testflow-store";
import { fetchRunnerExecution } from "@/lib/testflow-api";

const RunnerPage = () => {
  const executions = useTestFlowStore((state) => state.runnerExecutions);
  const selectedExecutionId = useTestFlowStore(
    (state) => state.selectedExecutionId,
  );
  const setSelectedExecution = useTestFlowStore(
    (state) => state.setSelectedExecution,
  );
  const startRun = useTestFlowStore((state) => state.startRun);
  const scenarios = useTestFlowStore((state) => state.scenarios);

  const [parallelism, setParallelism] = useState(1);
  const [logFilter, setLogFilter] = useState("");

  const activeExecution =
    executions.find((execution) => execution.id === selectedExecutionId) ??
    executions[0];

  const filteredLogs = useMemo(() => {
    if (!activeExecution) return [];
    if (!logFilter) return activeExecution.logs;

    return activeExecution.logs.filter((log) =>
      log.message.toLowerCase().includes(logFilter.toLowerCase()),
    );
  }, [activeExecution, logFilter]);

  // Polling статуса выполнения для активного прогона
  useEffect(() => {
    if (!activeExecution) return;

    // Если выполнение завершено, не обновляем
    if (
      activeExecution.status === "completed" ||
      activeExecution.status === "failed"
    ) {
      return;
    }

    const interval = setInterval(async () => {
      try {
        const updated = await fetchRunnerExecution(activeExecution.id);
        // Обновляем выполнение в store
        useTestFlowStore.setState((state) => ({
          runnerExecutions: state.runnerExecutions.map((e) =>
            e.id === updated.id ? updated : e,
          ),
        }));
      } catch (error) {
        console.error("Failed to fetch execution status", error);
      }
    }, 500); // Обновление каждые 500мс

    return () => clearInterval(interval);
  }, [activeExecution?.id, activeExecution?.status]);

  return (
    <div className="grid grid-cols-1 gap-4 xl:grid-cols-3">
      <Card className="border border-white/10 bg-white/5 xl:col-span-2">
        <CardHeader className="flex-wrap items-center gap-3">
          <div>
            <div className="text-sm font-semibold text-white">
              Управление прогонами
            </div>
            <div className="text-xs text-slate-400">
              сквозные цепочки, контроль параллелизма
            </div>
          </div>
          <div className="ml-auto flex gap-2">
            <Input
              className="max-w-[140px]"
              label="Параллельность"
              size="sm"
              type="number"
              value={parallelism.toString()}
              onChange={(event) => setParallelism(Number(event.target.value))}
            />
            <Button
              color="primary"
              startContent={<PlayCircleIcon className="h-4 w-4" />}
              onPress={() =>
                scenarios[0] && startRun(scenarios[0].id, { parallelism })
              }
            >
              Запустить
            </Button>
            <Button
              startContent={<PauseCircleIcon className="h-4 w-4" />}
              variant="flat"
            >
              Пауза
            </Button>
          </div>
        </CardHeader>
        <CardBody className="grid grid-cols-1 gap-3 xl:grid-cols-2">
          <div className="rounded-2xl border border-white/10 p-4">
            <div className="text-sm font-semibold text-white">
              Очередь прогонов
            </div>
            <div className="mt-2 max-h-[280px] space-y-2 overflow-y-auto">
              {executions.map((execution) => (
                <button
                  key={execution.id}
                  className={`w-full rounded-xl border px-3 py-2 text-left ${
                    execution.id === activeExecution?.id
                      ? "border-primary/60 bg-primary/5"
                      : "border-white/10"
                  }`}
                  type="button"
                  onClick={() => setSelectedExecution(execution.id)}
                >
                  <div className="flex justify-between text-sm font-semibold text-white">
                    <span>{execution.scenarioId}</span>
                    <Chip
                      color={
                        execution.status === "running"
                          ? "warning"
                          : execution.status === "completed"
                            ? "success"
                            : "default"
                      }
                      size="sm"
                    >
                      {execution.status}
                    </Chip>
                  </div>
                  <div className="text-xs text-slate-400">
                    {Math.round(execution.progress * 100)}% •{" "}
                    {execution.steps.length} шагов
                  </div>
                </button>
              ))}
            </div>
          </div>

          {activeExecution && (
            <div className="space-y-3 rounded-2xl border border-white/10 p-4">
              <div className="text-sm font-semibold text-white">
                Текущий прогон
              </div>
              <div className="text-xs text-slate-400">
                старт: {new Date(activeExecution.startedAt).toLocaleString()}
              </div>
              <div className="h-2 rounded-full bg-white/5">
                <div
                  className="h-2 rounded-full bg-primary"
                  style={{
                    width: `${Math.round(activeExecution.progress * 100)}%`,
                  }}
                />
              </div>
              <div className="max-h-[240px] space-y-2 overflow-y-auto">
                {activeExecution.steps.map((step) => (
                  <div
                    key={step.stepId}
                    className="flex items-center justify-between rounded-lg border border-white/10 px-3 py-2"
                  >
                    <div>
                      <div className="text-xs text-slate-400">
                        {step.stepId}
                      </div>
                      <div className="text-xs text-slate-500">
                        {step.durationMs ? `${step.durationMs}ms` : "—"}
                      </div>
                    </div>
                    <Chip
                      color={
                        step.status === "success"
                          ? "success"
                          : step.status === "failed"
                            ? "danger"
                            : step.status === "running"
                              ? "warning"
                              : "default"
                      }
                      size="sm"
                    >
                      {step.status}
                    </Chip>
                  </div>
                ))}
              </div>
            </div>
          )}
        </CardBody>
      </Card>

      <Card className="border border-white/10">
        <CardHeader className="flex items-center gap-3">
          <div>
            <div className="text-sm font-semibold text-white">
              Логи и данные
            </div>
            <div className="text-xs text-slate-400">WebSocket / SSE поток</div>
          </div>
          <Input
            className="ml-auto"
            placeholder="Фильтр по тексту"
            size="sm"
            value={logFilter}
            onChange={(event) => setLogFilter(event.target.value)}
          />
        </CardHeader>
        <CardBody className="max-h-[600px] space-y-2 overflow-y-auto">
          {filteredLogs.map((log) => (
            <div
              key={log.id}
              className="rounded-xl border border-white/10 bg-white/5 p-3 font-mono text-xs text-white"
            >
              <div className="flex justify-between">
                <Chip
                  color={
                    log.level === "error"
                      ? "danger"
                      : log.level === "warn"
                        ? "warning"
                        : "default"
                  }
                  size="sm"
                  variant="flat"
                >
                  {log.level}
                </Chip>
                <div className="text-[10px] text-slate-400">
                  {log.timestamp}
                </div>
              </div>
              <div className="mt-2 text-white">{log.message}</div>
              {log.payloadPreview && (
                <pre className="mt-2 rounded-lg bg-black/80 p-2 text-white/80">
                  {JSON.stringify(log.payloadPreview, null, 2)}
                </pre>
              )}
            </div>
          ))}
          {!filteredLogs.length && (
            <div className="text-xs text-slate-500">Пока нет логов.</div>
          )}
        </CardBody>
        <div className="flex gap-2 border-t border-white/10 px-4 py-3">
          <Button
            startContent={<CloudArrowDownIcon className="h-4 w-4" />}
            variant="light"
          >
            Экспорт
          </Button>
          <Button
            startContent={<BoltIcon className="h-4 w-4" />}
            variant="flat"
          >
            Подписаться на WebSocket
          </Button>
        </div>
      </Card>
    </div>
  );
};

export default RunnerPage;

