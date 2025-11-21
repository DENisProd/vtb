import { useEffect, useMemo, useState } from "react";
import {
  Button,
  Card,
  CardBody,
  CardHeader,
  Chip,
  Textarea,
  Select,
  SelectItem,
  Input,
  Spinner,
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
  const loading = useTestFlowStore((state) => state.loading);
  const error = useTestFlowStore((state) => state.error);
  const generate = useTestFlowStore((state) => state.generateTestDataTemplates);
  const setActive = useTestFlowStore((state) => state.setActiveTemplate);
  const globalOverrides = useTestFlowStore((state) => state.globalOverrides);
  const setGlobalOverride = useTestFlowStore((state) => state.setGlobalOverride);
  const mappingResult = useTestFlowStore((state) => state.mappingResult);
  const commonOverrides = useTestFlowStore((state) => state.commonOverrides);
  const setCommonOverride = useTestFlowStore((state) => state.setCommonOverride);

  const [generationType, setGenerationType] = useState<"CLASSIC" | "AI">("CLASSIC");
  const [scenario, setScenario] = useState("positive");
  const [variantsCount, setVariantsCount] = useState(1);

  const activeTemplate = useMemo(
    () =>
      templates.find((template) => template.id === activeTemplateId) ??
      templates[0],
    [templates, activeTemplateId],
  );

  useEffect(() => {
    if (!templates.length && !loading) {
      generate({ generationType, scenario, variantsCount });
    }
  }, []);

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
            <Select
              className="max-w-[140px]"
              label="Тип"
              selectedKeys={[generationType]}
              size="sm"
              onSelectionChange={(keys) => {
                const selected = Array.from(keys)[0] as "CLASSIC" | "AI";
                setGenerationType(selected);
              }}
            >
              <SelectItem key="CLASSIC">
                CLASSIC
              </SelectItem>
              <SelectItem key="AI">
                AI
              </SelectItem>
            </Select>
            <Select
              className="max-w-[140px]"
              label="Сценарий"
              selectedKeys={[scenario]}
              size="sm"
              onSelectionChange={(keys) => {
                const selected = Array.from(keys)[0] as string;
                setScenario(selected);
              }}
            >
              <SelectItem key="positive">
                positive
              </SelectItem>
              <SelectItem key="negative">
                negative
              </SelectItem>
              <SelectItem key="edge_case">
                edge_case
              </SelectItem>
            </Select>
            <Input
              className="max-w-[100px]"
              label="Вариантов"
              size="sm"
              type="number"
              value={variantsCount.toString()}
              onChange={(e) => setVariantsCount(Number(e.target.value) || 1)}
            />
            <Button
              color="secondary"
              isDisabled={loading}
              isLoading={loading}
              startContent={loading ? undefined : <BoltIcon className="h-4 w-4" />}
              variant="flat"
              onPress={() => generate({ generationType, scenario, variantsCount })}
            >
              {loading ? "Генерация..." : "Сгенерировать"}
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

      {mappingResult?.commonFields && mappingResult.commonFields.length > 0 && (
        <Card className="border border-white/10">
          <CardHeader className="justify-between">
            <div>
              <div className="text-sm font-semibold text-white">Общие поля (map)</div>
              <div className="text-xs text-slate-400">значения из сопоставления, применяются ко всем шагам</div>
            </div>
          </CardHeader>
          <CardBody className="space-y-3">
            {mappingResult.commonFields.map((cf) => {
              const name = cf.fieldName;
              const current = (commonOverrides ?? {})[name] ?? "";
              const textValue = typeof current === "string" ? current : JSON.stringify(current, null, 2);
              return (
                <div key={name} className="rounded-xl border border-white/10 bg-white/80 p-3">
                  <div className="flex flex-wrap items-center gap-2 text-sm font-semibold text-slate-900">
                    {name}
                    <Chip size="sm" variant="flat">{cf.fieldType}</Chip>
                    {cf.required && <Chip size="sm" variant="bordered">required</Chip>}
                  </div>
                  <Textarea
                    className="mt-2"
                    value={textValue as string}
                    minRows={2}
                    onChange={(e) => {
                      const raw = e.target.value;
                      let val: unknown = raw;
                      if ((cf.fieldType ?? "").toLowerCase() === "json") {
                        try { val = JSON.parse(raw); } catch { val = raw; }
                      }
                      setCommonOverride(name, val);
                    }}
                  />
                  {cf.description && (
                    <div className="mt-1 text-xs text-slate-500">{cf.description}</div>
                  )}
                </div>
              );
            })}
          </CardBody>
        </Card>
      )}

      {error && (
        <Card className="border border-danger/30 bg-danger/5">
          <CardBody>
            <div className="text-sm text-danger">{error}</div>
          </CardBody>
        </Card>
      )}

      {loading && !activeTemplate && (
        <Card className="border border-white/10">
          <CardBody className="flex items-center justify-center py-12">
            <Spinner size="lg" />
            <div className="mt-4 text-sm text-slate-400">
              Генерация тестовых данных...
            </div>
          </CardBody>
        </Card>
      )}

      {activeTemplate && (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
          <Card className="border border-white/10">
            <CardHeader className="justify-between">
              <div>
                <div className="text-sm font-semibold text-white">Общие поля</div>
                <div className="text-xs text-slate-400">значения применяются поверх генерации</div>
              </div>
            </CardHeader>
            <CardBody className="space-y-3">
              {(activeTemplate.contexts.filter((c) => c.scope === "global")[0]?.fields ?? []).map((field) => {
                const stepId = field.dependsOn?.stepId ?? "";
                const name = field.dependsOn?.field ?? field.key;
                const overrideKey = `${stepId}.${name}`;
                const current = (globalOverrides ?? {})[overrideKey] ?? field.value;
                const textValue = typeof current === "string" ? current : JSON.stringify(current, null, 2);
                return (
                  <div key={field.key} className="rounded-xl border border-white/10 bg-white/80 p-3">
                    <div className="flex flex-wrap items-center gap-2 text-sm font-semibold text-slate-900">
                      {field.label}
                      {field.dependsOn && (
                        <Chip size="sm" variant="flat">{field.dependsOn.stepId}.{field.dependsOn.field}</Chip>
                      )}
                    </div>
                    <Textarea
                      className="mt-2"
                      value={textValue as string}
                      minRows={2}
                      onChange={(e) => {
                        const raw = e.target.value;
                        let val: unknown = raw;
                        if (field.type === "json") {
                          try {
                            val = JSON.parse(raw);
                          } catch {
                            val = raw;
                          }
                        }
                        setGlobalOverride(stepId, name, val);
                      }}
                    />
                  </div>
                );
              })}
            </CardBody>
          </Card>

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

