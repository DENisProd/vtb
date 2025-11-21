import { useEffect, useMemo, useState } from "react";
import {
  Button,
  Card,
  CardBody,
  CardHeader,
  Chip,
  Select,
  SelectItem,
  Textarea,
} from "@heroui/react";
import {
  AdjustmentsVerticalIcon,
  ChatBubbleLeftEllipsisIcon,
  ExclamationCircleIcon,
} from "@heroicons/react/24/outline";

import { useTestFlowStore } from "@/stores/testflow-store";

const severityOptions = [
  { key: "all", label: "Все" },
  { key: "critical", label: "Критично" },
  { key: "error", label: "Ошибка" },
  { key: "warning", label: "Предупреждение" },
  { key: "info", label: "Инфо" },
];

const AnalysisPage = () => {
  const issues = useTestFlowStore((state) => state.analysisIssues);
  const aiModels = useTestFlowStore((state) => state.aiModels as any);
  const selectedModelId = useTestFlowStore((state) => (state as any).selectedModelId as number | null);
  const loadAiModels = useTestFlowStore((state) => state.loadAiModels);
  const setSelectedModelId = useTestFlowStore((state) => state.setSelectedModelId);
  const [severityFilter, setSeverityFilter] = useState<string>("all");
  const [comment, setComment] = useState("");

  const filteredIssues = useMemo(() => {
    if (severityFilter === "all") return issues;

    return issues.filter((issue) => issue.severity === severityFilter);
  }, [issues, severityFilter]);

  useEffect(() => {
    loadAiModels();
  }, [loadAiModels]);

  return (
    <div className="space-y-6">
      <Card className="border border-warning/20 bg-warning/5">
        <CardHeader className="items-start gap-3">
          <div className="rounded-full bg-warning/20 p-2">
            <ExclamationCircleIcon className="h-5 w-5 text-warning" />
          </div>
          <div>
            <h1 className="text-xl font-semibold text-white">
              NLP анализ артефактов
            </h1>
            <p className="text-sm text-slate-400">
              Консистентность контрактов, отсутствие валидации и потенциальные
              точки отказа. Добавляйте комментарии и назначайте задачи
              аналитикам.
            </p>
          </div>
        </CardHeader>
        <CardBody className="flex flex-wrap gap-3">
          <Select
            className="max-w-xs"
            label="Серьёзность"
            selectedKeys={[severityFilter]}
            size="sm"
            onChange={(event) => setSeverityFilter(event.target.value)}
          >
            {severityOptions.map((option) => (
              <SelectItem key={option.key}>{option.label}</SelectItem>
            ))}
          </Select>
          <Select
            className="max-w-xs"
            label="Модель ИИ"
            selectedKeys={selectedModelId != null ? [String(selectedModelId)] : []}
            size="sm"
            onChange={(event) => setSelectedModelId(Number(event.target.value))}
          >
            {(aiModels || []).map((m: any) => (
              <SelectItem key={m.id}>{m.name}</SelectItem>
            ))}
          </Select>
          <Button
            color="secondary"
            startContent={<AdjustmentsVerticalIcon className="h-4 w-4" />}
            variant="flat"
          >
            Фильтры
          </Button>
        </CardBody>
      </Card>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <Card className="border border-white/10 lg:col-span-2">
          <CardHeader>
            <div>
              <div className="text-sm font-semibold text-white">
                Найденные несогласованности
              </div>
              <div className="text-xs text-slate-500">
                {filteredIssues.length} из {issues.length} отображаются
              </div>
            </div>
          </CardHeader>
          <CardBody className="space-y-4">
            {filteredIssues.map((issue) => (
              <div
                key={issue.id}
                className="rounded-2xl border border-white/10 bg-white/5 p-4"
              >
                <div className="flex items-center gap-2">
                  <Chip
                    color={
                      issue.severity === "critical"
                        ? "danger"
                        : issue.severity === "error"
                          ? "danger"
                          : issue.severity === "warning"
                            ? "warning"
                            : "default"
                    }
                    size="sm"
                    variant="flat"
                  >
                    {issue.severity}
                  </Chip>
                  <div className="text-sm font-semibold text-white">
                    {issue.title}
                  </div>
                  <div className="ml-auto text-xs text-slate-500">
                    {new Date(issue.createdAt).toLocaleString()}
                  </div>
                </div>
                <div className="mt-2 text-sm text-slate-300">
                  {issue.details}
                </div>
                {issue.suggestedAction && (
                  <div className="mt-2 text-xs text-slate-400">
                    Рекомендация: {issue.suggestedAction}
                  </div>
                )}
                <div className="mt-3 flex flex-wrap gap-2 text-xs text-slate-500">
                  <span>Источник: {issue.artifactId ?? "—"}</span>
                  <span>Категория: {issue.category}</span>
                  <span>Уверенность: {(issue.confidence * 100).toFixed(0)}%</span>
                </div>
              </div>
            ))}
          </CardBody>
        </Card>

        <Card className="border border-white/10">
          <CardHeader className="items-start">
            <div>
              <div className="text-sm font-semibold text-white">
                Комментарии и задачи
              </div>
              <div className="text-xs text-slate-500">
                назначайте аналитиков, добавляйте теги
              </div>
            </div>
          </CardHeader>
          <CardBody className="space-y-3">
            <Textarea
              label="Комментарий"
              minRows={4}
              placeholder="Опишите, почему это критично, и какие действия нужны"
              value={comment}
              onChange={(event) => setComment(event.target.value)}
            />
            <Button
              color="primary"
              isDisabled={!comment}
              startContent={<ChatBubbleLeftEllipsisIcon className="h-4 w-4" />}
              onPress={() => setComment("")}
            >
              Создать обсуждение
            </Button>
          </CardBody>
        </Card>
      </div>
    </div>
  );
};

export default AnalysisPage;

