import { useEffect, useMemo, useState } from "react";
import {
  Button,
  Card,
  CardBody,
  CardFooter,
  CardHeader,
  Chip,
  Input,
  Select,
  SelectItem,
  Textarea,
} from "@heroui/react";
import {
  ArrowUpRightIcon,
  PencilSquareIcon,
  PlayCircleIcon,
  TagIcon,
} from "@heroicons/react/24/outline";

import { TestFlowCanvas } from "@/components/testflow/TestFlowCanvas";
import { useTestFlowStore } from "@/stores/testflow-store";
import type { ScenarioStep } from "@/types/testflow";

const methodOptions = ["GET", "POST", "PUT", "PATCH", "DELETE"];

const ScenariosPage = () => {
  const scenarios = useTestFlowStore((state) => state.scenarios);
  const selectedScenarioId = useTestFlowStore(
    (state) => state.selectedScenarioId,
  );
  const setSelectedScenario = useTestFlowStore(
    (state) => state.setSelectedScenario,
  );
  const startRun = useTestFlowStore((state) => state.startRun);
  const addScenarioStep = useTestFlowStore((state) => state.addScenarioStep);
  const connectScenarioSteps = useTestFlowStore(
    (state) => state.connectScenarioSteps,
  );

  const [search, setSearch] = useState("");
  const [selectedStepId, setSelectedStepId] = useState<string | null>(null);
  const [linkSource, setLinkSource] = useState<string | null>(null);
  const [newStep, setNewStep] = useState({
    title: "",
    endpoint: "",
    method: "GET",
  });

  const filteredScenarios = useMemo(() => {
    if (!search) return scenarios;
    return scenarios.filter((scenario) =>
      scenario.name.toLowerCase().includes(search.toLowerCase()),
    );
  }, [scenarios, search]);

  const activeScenario =
    filteredScenarios.find((scenario) => scenario.id === selectedScenarioId) ??
    filteredScenarios[0];

  const selectedStep =
    activeScenario?.steps.find((step) => step.id === selectedStepId) ??
    activeScenario?.steps[0];

  useEffect(() => {
    if (activeScenario && !selectedStepId) {
      setSelectedStepId(activeScenario.steps[0]?.id ?? null);
    }
  }, [activeScenario, selectedStepId]);

  useEffect(() => {
    setLinkSource(null);
  }, [activeScenario?.id]);

  const handleAddStep = () => {
    if (!activeScenario || !newStep.title || !newStep.endpoint) return;
    addScenarioStep(activeScenario.id, newStep);
    setNewStep((prev) => ({ ...prev, title: "", endpoint: "" }));
  };

  const handleLink = (targetId: string) => {
    if (!activeScenario) return;
    if (!linkSource) {
      setLinkSource(targetId);
      return;
    }
    if (linkSource === targetId) {
      setLinkSource(null);
      return;
    }
    connectScenarioSteps(activeScenario.id, linkSource, targetId);
    setLinkSource(null);
  };

  return (
    <div className="grid grid-cols-1 gap-5 lg:grid-cols-3">
      <Card className="app-card">
        <CardHeader className="flex-col items-start gap-2">
          <div className="text-sm font-semibold text-[var(--app-text)]">
            Сценарии
          </div>
          <Input
            placeholder="Поиск по названию или тегам"
            size="sm"
            startContent={<TagIcon className="h-4 w-4 text-muted" />}
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </CardHeader>
        <CardBody className="max-h-[70vh] space-y-3 overflow-y-auto pr-2">
          {filteredScenarios.map((scenario) => (
            <button
              key={scenario.id}
              className={`w-full rounded-2xl border p-4 text-left transition-all ${
                scenario.id === activeScenario?.id
                  ? "border-[var(--app-primary)] bg-[var(--app-bg-alt)] shadow-lg"
                  : "border-white/15 hover:border-[var(--app-primary)]/50"
              }`}
              type="button"
              onClick={() => setSelectedScenario(scenario.id)}
            >
              <div className="flex items-center gap-2">
                <div className="text-sm font-semibold text-[var(--app-text)]">
                  {scenario.name}
                </div>
                <Chip size="sm" variant="flat">
                  {scenario.status}
                </Chip>
              </div>
              <div className="mt-1 text-xs text-muted">
                {scenario.steps.length} шагов • покрытие{" "}
                {(scenario.coverage * 100).toFixed(0)}%
              </div>
              <div className="mt-2 flex flex-wrap gap-1">
                {scenario.tags?.map((tag) => (
                  <Chip key={tag} size="sm" variant="bordered">
                    {tag}
                  </Chip>
                ))}
              </div>
            </button>
          ))}
        </CardBody>
        <CardFooter className="flex gap-2">
          <Button className="btn-ghost">Новый сценарий</Button>
          <Button
            className="btn-outline"
            startContent={<ArrowUpRightIcon className="w-4 h-4" />}
          >
            Импортировать
          </Button>
        </CardFooter>
      </Card>

      <div className="flex flex-col gap-4 lg:col-span-2">
        {activeScenario && (
          <>
            <Card className="app-card">
              <CardHeader className="flex-wrap gap-3">
                <div>
                  <div className="text-sm font-semibold text-[var(--app-text)]">
                    {activeScenario.name}
                  </div>
                  <div className="text-xs text-muted">
                    {activeScenario.steps.length} шагов • риск{" "}
                    {activeScenario.riskLevel === "low"
                      ? "низкий"
                      : activeScenario.riskLevel === "medium"
                        ? "средний"
                        : "высокий"}
                  </div>
                </div>
                <div className="ml-auto flex gap-2">
                  <Button
                    className="btn-primary"
                    startContent={<PlayCircleIcon className="w-4 h-4" />}
                    onPress={() => startRun(activeScenario.id)}
                  >
                    Запустить
                  </Button>
                  <Button
                    className="btn-ghost"
                    startContent={<PencilSquareIcon className="w-4 h-4" />}
                  >
                    История правок
                  </Button>
                </div>
              </CardHeader>
              <CardBody>
                <TestFlowCanvas
                  selectedNodeId={selectedStep?.id}
                  onSelect={(nodeId) => setSelectedStepId(nodeId)}
                />
              </CardBody>
            </Card>

            <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
              <Card className="app-card">
                <CardHeader className="flex-col items-start gap-2">
                  <div className="text-sm font-semibold text-[var(--app-text)]">
                    Шаги сценария
                  </div>
                  <div className="text-xs text-muted">
                    drag & drop (скоро) • быстрые действия
                  </div>
                </CardHeader>
                <CardBody className="max-h-[320px] space-y-3 overflow-y-auto pr-2">
                  {activeScenario.steps.map((step) => (
                    <StepRow
                      key={step.id}
                      isActive={step.id === selectedStep?.id}
                      isLinking={linkSource === step.id}
                      step={step}
                      onSelect={() => setSelectedStepId(step.id)}
                      onLink={() => handleLink(step.id)}
                    />
                  ))}
                </CardBody>
                <CardFooter className="text-xs text-muted">
                  {linkSource
                    ? "Выберите второй шаг, чтобы завершить связь"
                    : "Нажмите «Связать», чтобы начать построение цепочки"}
                </CardFooter>
              </Card>

              {selectedStep && (
                <Card className="app-card">
                  <CardHeader className="flex-col items-start gap-1">
                    <div className="text-sm font-semibold text-[var(--app-text)]">
                      Детали шага
                    </div>
                    <div className="text-xs text-muted">{selectedStep.title}</div>
                  </CardHeader>
                  <CardBody className="space-y-3">
                    <div className="text-xs uppercase text-muted">Эндпоинт</div>
                    <div className="rounded-lg border border-white/15 bg-white/70 px-3 py-2 font-mono text-xs text-[var(--app-text)]">
                      {selectedStep.method} {selectedStep.endpoint}
                    </div>
                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <div className="text-xs uppercase text-muted">
                          Ожидаемый статус
                        </div>
                        <div className="text-sm font-semibold text-[var(--app-text)]">
                          {selectedStep.expectedStatus}
                        </div>
                      </div>
                      <div>
                        <div className="text-xs uppercase text-muted">
                          Таймаут
                        </div>
                        <div className="text-sm font-semibold text-[var(--app-text)]">
                          {Math.round((selectedStep.timeoutMs ?? 0) / 1000)}с
                        </div>
                      </div>
                    </div>
                    <div>
                      <div className="mb-1 text-xs uppercase text-muted">
                        Тело запроса
                      </div>
                      <Textarea
                        isReadOnly
                        minRows={5}
                        value={JSON.stringify(
                          selectedStep.payload ?? {},
                          null,
                          2,
                        )}
                      />
                    </div>
                    <div className="flex flex-wrap gap-2">
                      {selectedStep.preconditions?.map((pre) => (
                        <Chip key={pre} size="sm" variant="flat">
                          зависит от {pre}
                        </Chip>
                      ))}
                      {selectedStep.outputs?.map((out) => (
                        <Chip key={out} size="sm" variant="bordered">
                          отдаёт {out}
                        </Chip>
                      ))}
                    </div>
                  </CardBody>
                </Card>
              )}
            </div>
          </>
        )}
      </div>

      {activeScenario && (
        <Card className="app-card lg:col-span-3">
          <CardHeader className="flex-col items-start gap-2">
            <div className="text-sm font-semibold text-[var(--app-text)]">
              Новый блок цепочки
            </div>
            <div className="text-xs text-muted">
              Задайте параметры шага и подключите его к соседним узлам.
            </div>
          </CardHeader>
          <CardBody className="grid grid-cols-1 gap-4 md:grid-cols-3">
            <Input
              label="Название шага"
              placeholder="Создать заявку"
              value={newStep.title}
              onChange={(event) =>
                setNewStep((prev) => ({ ...prev, title: event.target.value }))
              }
            />
            <Input
              label="Endpoint"
              placeholder="/api/v1/orders"
              value={newStep.endpoint}
              onChange={(event) =>
                setNewStep((prev) => ({ ...prev, endpoint: event.target.value }))
              }
            />
            <Select
              label="Метод"
              selectedKeys={[newStep.method]}
              onChange={(event) =>
                setNewStep((prev) => ({ ...prev, method: event.target.value }))
              }
            >
              {methodOptions.map((method) => (
                <SelectItem key={method}>{method}</SelectItem>
              ))}
            </Select>
          </CardBody>
          <CardFooter className="flex flex-wrap justify-between gap-3">
            <div className="text-xs text-muted">
              После добавления используйте «Связать» у шагов, чтобы задать порядок.
            </div>
            <Button
              className="btn-primary"
              isDisabled={!newStep.title || !newStep.endpoint}
              onPress={handleAddStep}
            >
              Добавить шаг
            </Button>
          </CardFooter>
        </Card>
      )}
    </div>
  );
};

const StepRow = ({
  step,
  isActive,
  isLinking,
  onSelect,
  onLink,
}: {
  step: ScenarioStep;
  isActive: boolean;
  isLinking: boolean;
  onSelect: () => void;
  onLink: () => void;
}) => (
  <button
    className={`w-full rounded-2xl border px-3 py-2 text-left ${
      isActive
        ? "border-[var(--app-primary)] bg-[var(--app-bg-alt)]"
        : "border-white/15 hover:border-[var(--app-primary)]/40"
    }`}
    type="button"
    onClick={onSelect}
  >
    <div className="flex items-center gap-2">
      <div className="text-xs text-muted">#{step.order}</div>
      <div className="text-sm font-semibold text-[var(--app-text)]">
        {step.title}
      </div>
      <Chip size="sm" variant="flat">
        {step.status}
      </Chip>
    </div>
    <div className="mt-2 flex items-center justify-between text-xs text-muted">
      <span>
        {step.method} {step.endpoint}
      </span>
      <Button
        className={`btn-ghost !px-2 !py-1 text-[11px] ${
          isLinking ? "!bg-[var(--app-primary)]/15" : ""
        }`}
        size="sm"
        onClick={(event) => {
          event.stopPropagation();
          onLink();
        }}
      >
        {isLinking ? "Выбрано" : "Связать"}
      </Button>
    </div>
  </button>
);

export default ScenariosPage;

