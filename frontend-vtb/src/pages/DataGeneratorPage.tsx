import { useEffect, useMemo } from "react";
import {
  Button,
  Card,
  CardBody,
  CardHeader,
  Chip,
  Textarea,
} from "@heroui/react";
import {
  ArrowsRightLeftIcon,
  BoltIcon,
  ClipboardDocumentIcon,
  CubeTransparentIcon,
} from "@heroicons/react/24/outline";

import { useTestFlowStore } from "@/stores/testflow-store";

const DataGeneratorPage = () => {
  const templates = useTestFlowStore((state) => state.templates);
  const activeTemplateId = useTestFlowStore((state) => state.activeTemplateId);
  const generate = useTestFlowStore((state) => state.generateTestDataTemplates);
  const setActive = useTestFlowStore((state) => state.setActiveTemplate);

  const activeTemplate = useMemo(
    () =>
      templates.find((template) => template.id === activeTemplateId) ??
      templates[0],
    [templates, activeTemplateId],
  );

  useEffect(() => {
    if (!templates.length) generate();
  }, [templates.length, generate]);

  return (
    <div className="space-y-5">
      <Card className="border border-secondary/30 bg-secondary/5">
        <CardHeader className="items-start gap-3">
          <div className="rounded-full bg-secondary/30 p-2">
            <CubeTransparentIcon className="h-5 w-5 text-secondary" />
          </div>
          <div>
            <div className="text-sm font-semibold text-white">
              Генератор тестовых данных
            </div>
            <div className="text-xs text-slate-400">
              Автогенерация связных данных с учётом зависимостей между шагами.
              Любое поле можно отредактировать.
            </div>
          </div>
          <div className="ml-auto flex gap-2">
            <Button
              color="secondary"
              startContent={<BoltIcon className="h-4 w-4" />}
              variant="flat"
              onPress={() => generate()}
            >
              Сгенерировать заново
            </Button>
            <Button
              startContent={<ClipboardDocumentIcon className="h-4 w-4" />}
              variant="light"
              onPress={async () => {
                const text = JSON.stringify(activeTemplate ?? {}, null, 2);
                try {
                  await navigator.clipboard.writeText(text);
                } catch {}
              }}
            >
              Экспорт JSON
            </Button>
          </div>
        </CardHeader>
      </Card>

      {activeTemplate && (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
          <Card className="border border-white/10 lg:col-span-2">
            <CardHeader className="justify-between">
              <div>
                <div className="text-sm font-semibold text-white">
                  {activeTemplate.name}
                </div>
                <div className="text-xs text-slate-400">
                  сид: {activeTemplate.seed ?? "—"}
                </div>
              </div>
              <Chip size="sm" variant="flat">
                контексты {activeTemplate.contexts.length}
              </Chip>
            </CardHeader>
            <CardBody className="space-y-4">
              {activeTemplate.contexts.map((context) => (
                <div
                  key={context.id}
                  className="rounded-2xl border border-white/10 bg-white/5 p-4"
                >
                  <div className="flex flex-wrap items-center gap-2">
                    <div className="text-sm font-semibold text-white">
                      {context.label}
                    </div>
                    <Chip size="sm" variant="flat">
                      {context.scope}
                    </Chip>
                    {context.relatedStepId && (
                      <Chip size="sm" variant="bordered">
                        шаг {context.relatedStepId}
                      </Chip>
                    )}
                  </div>
                  <div className="mt-3 space-y-3">
                    {context.fields.map((field) => (
                      <div
                        key={field.key}
                        className="rounded-xl border border-white/10 bg-white/80 p-3"
                      >
                        <div className="flex flex-wrap items-center gap-2 text-sm font-semibold text-slate-900">
                          {field.label}
                          <Chip size="sm" variant="flat">
                            {field.type}
                          </Chip>
                          {field.dependsOn && (
                            <Chip
                              color="primary"
                              size="sm"
                              startContent={
                                <ArrowsRightLeftIcon className="h-3 w-3" />
                              }
                              variant="flat"
                            >
                              {field.dependsOn.stepId}.{field.dependsOn.field}
                            </Chip>
                          )}
                        </div>
                        <Textarea
                          className="mt-2"
                          defaultValue={JSON.stringify(field.value, null, 2)}
                          minRows={2}
                        />
                        {field.reason && (
                          <div className="mt-1 text-xs text-slate-500">
                            {Math.round((field.confidence ?? 0) * 100)}% •{" "}
                            {field.reason}
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              ))}
            </CardBody>
          </Card>

          <Card className="border border-white/10">
            <CardHeader>
              <div>
                <div className="text-sm font-semibold text-white">Шаблоны</div>
                <div className="text-xs text-slate-400">
                  сохраняйте разные наборы данных
                </div>
              </div>
            </CardHeader>
            <CardBody className="space-y-3">
              {templates.map((template) => (
                <button
                  key={template.id}
                  className={`w-full rounded-2xl border px-3 py-2 text-left ${
                    template.id === activeTemplate.id
                      ? "border-secondary/60 bg-secondary/10"
                      : "border-white/10"
                  }`}
                  type="button"
                  onClick={() => setActive(template.id)}
                >
                  <div className="text-sm font-semibold text-white">
                    {template.name}
                  </div>
                  <div className="text-xs text-slate-400">
                    контекстов {template.contexts.length} • обновлено{" "}
                    {new Date(template.updatedAt).toLocaleTimeString()}
                  </div>
                </button>
              ))}
            </CardBody>
          </Card>
        </div>
      )}
    </div>
  );
};

export default DataGeneratorPage;

