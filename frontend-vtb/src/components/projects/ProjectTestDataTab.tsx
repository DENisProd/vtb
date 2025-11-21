import { Card, CardBody, CardHeader, Button, Chip, Select, SelectItem, Textarea, Input, Accordion, AccordionItem } from "@heroui/react";
import { BoltIcon, ClipboardDocumentIcon } from "@heroicons/react/24/outline";
import Editor, { loader } from "@monaco-editor/react";
import { ProjectDto } from "@/lib/testflow-api";
import { useTestFlowStore } from "@/stores/testflow-store";
import { useMemo, useState } from "react";

interface ProjectTestDataTabProps {
  project: ProjectDto;
}

export function ProjectTestDataTab({ project }: ProjectTestDataTabProps) {
  const templates = useTestFlowStore((s) => s.templates);
  const activeTemplateId = useTestFlowStore((s) => s.activeTemplateId);
  const setActiveTemplate = useTestFlowStore((s) => s.setActiveTemplate);
  const generateTemplates = useTestFlowStore((s) => s.generateTestDataTemplates);
  const loading = useTestFlowStore((s) => s.loading);
  const error = useTestFlowStore((s) => s.error);
  const globalOverrides = useTestFlowStore((s) => s.globalOverrides);
  const setGlobalOverride = useTestFlowStore((s) => s.setGlobalOverride);
  const commonOverrides = useTestFlowStore((s) => s.commonOverrides);
  const setCommonOverride = useTestFlowStore((s) => s.setCommonOverride);

  const [generationType, setGenerationType] = useState<"CLASSIC" | "AI">("CLASSIC");
  const [scenario, setScenario] = useState("positive");
  const [variantsCount, setVariantsCount] = useState(1);
  const [monacoOk, setMonacoOk] = useState(false);

  // Configure local Monaco assets and try to initialize; fall back to Textarea on failure
  useMemo(() => {
    try {
      loader.config({ paths: { vs: "/vs" } });
    } catch {}
    try {
      loader
        .init()
        .then(() => setMonacoOk(true))
        .catch(() => setMonacoOk(false));
    } catch {
      setMonacoOk(false);
    }
    return undefined;
  }, []);

  const handleGenerate = async () => {
    await generateTemplates({
      openApiJson: project.openApiJson,
      mappingResult: project.mappingResult,
      generationType,
      scenario,
      variantsCount,
    });
  };

  const activeTemplate = templates.find((t) => t.id === activeTemplateId) ?? templates[0];
  const mappingResult = project.mappingResult;

  return (
    <div className="space-y-4">
      <Card className="app-card">
        <CardHeader className="items-start gap-3">
          <div>
            <div className="text-sm font-semibold text-[var(--app-text)]">Тестовые данные</div>
            <div className="text-xs text-muted">Автогенерация данных и наложение общих полей</div>
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
              <SelectItem key="CLASSIC">CLASSIC</SelectItem>
              <SelectItem key="AI">AI</SelectItem>
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
              <SelectItem key="positive">positive</SelectItem>
              <SelectItem key="negative">negative</SelectItem>
              <SelectItem key="edge_case">edge_case</SelectItem>
            </Select>
            <Button
              className="btn-primary"
              isDisabled={loading || !project.openApiJson || !project.mappingResult || !project.bpmnXml}
              isLoading={loading}
              startContent={loading ? undefined : <BoltIcon className="w-4 h-4" />}
              onPress={handleGenerate}
            >
              {loading ? "Генерация..." : "Сгенерировать"}
            </Button>
          </div>
        </CardHeader>
        <CardBody>
          {error ? (
            <div className="text-sm text-danger">{error}</div>
          ) : (
            <div className="text-sm text-muted">
              Тестовые данные будут сгенерированы на основе OpenAPI схемы и сопоставления BPMN↔API
            </div>
          )}
        </CardBody>
      </Card>

      {((mappingResult?.commonFields?.length ?? 0) > 0 || (mappingResult?.secretFields?.length ?? 0) > 0) && (
        <Card className="app-card">
          <CardHeader className="justify-between">
            <div className="text-sm font-semibold text-[var(--app-text)]">Общие поля (map)</div>
            <div className="text-xs text-muted">значения из сопоставления, применяются ко всем шагам</div>
          </CardHeader>
          <CardBody className="space-y-3">
            {[
              ...(mappingResult?.commonFields ?? []).map((f) => ({
                name: f.fieldName,
                type: f.fieldType,
                required: f.required,
                description: f.description,
                secret: false,
              })),
              ...(mappingResult?.secretFields ?? []).map((f) => ({
                name: f.fieldName,
                type: f.fieldType ?? f.dataType ?? "string",
                required: f.required ?? true,
                description: f.description,
                secret: true,
              })),
            ].map((cf) => {
              const current = (commonOverrides ?? {})[cf.name] ?? "";
              const textValue = typeof current === "string" ? current : JSON.stringify(current, null, 2);
              const isJson = (cf.type ?? "").toLowerCase() === "json";
              const containerCls = cf.secret
                ? "rounded-xl border border-warning/40 bg-warning/5 p-3"
                : cf.required
                ? "rounded-xl border border-primary/40 bg-primary/5 p-3"
                : "rounded-xl border border-white/10 bg-white/80 p-3";
              return (
                <div key={cf.name} className={containerCls}>
                  <div className="flex flex-wrap items-center gap-2 text-sm font-semibold text-slate-900">
                    {cf.name}
                    <Chip size="sm" variant="flat">{cf.type}</Chip>
                    {cf.required && <Chip size="sm" variant="bordered">required</Chip>}
                    {cf.secret && <Chip size="sm" color="warning" variant="flat">secret</Chip>}
                  </div>
                  {isJson && monacoOk ? (
                    <Accordion className="mt-2">
                      <AccordionItem key={`${cf.name}-json`} title="JSON">
                        <Editor
                          height="200px"
                          defaultLanguage="json"
                          theme="vs-dark"
                          value={textValue as string}
                          onChange={(value) => {
                            const raw = value ?? "";
                            let val: unknown = raw;
                            try { val = JSON.parse(raw); } catch { val = raw; }
                            setCommonOverride(cf.name, val);
                          }}
                        />
                      </AccordionItem>
                    </Accordion>
                  ) : cf.secret ? (
                    <Input
                      className="mt-2"
                      type="password"
                      value={(textValue as string) ?? ""}
                      onChange={(e) => setCommonOverride(cf.name, e.target.value)}
                    />
                  ) : (
                    <Textarea
                      className="mt-2"
                      value={textValue as string}
                      minRows={2}
                      onChange={(e) => setCommonOverride(cf.name, e.target.value)}
                    />
                  )}
                  {cf.description && (
                    <div className="mt-1 text-xs text-slate-500">{cf.description}</div>
                  )}
                </div>
              );
            })}
          </CardBody>
        </Card>
      )}

      {templates.length > 0 && (
        <Card className="app-card">
          <CardHeader className="justify-between">
            <div className="text-sm font-semibold text-[var(--app-text)]">Варианты данных</div>
            <Select
              selectedKeys={activeTemplate ? [activeTemplate.id] : []}
              onSelectionChange={(keys) => {
                const id = Array.from(keys)[0] as string;
                if (id) setActiveTemplate(id);
              }}
              className="max-w-xs"
              aria-label="Выбор варианта"
            >
              {templates.map((tpl) => (
                <SelectItem key={tpl.id}>
                  {tpl.name}
                </SelectItem>
              ))}
            </Select>
          </CardHeader>
          <CardBody>
            <div className="flex items-center gap-2 mb-3">
              <Chip size="sm" variant="flat">Контекстов: {activeTemplate?.contexts.length ?? 0}</Chip>
            </div>
            <Accordion>
              <AccordionItem key="template-json" title="JSON шаблона">
                {monacoOk ? (
                  <Editor
                    height="240px"
                    defaultLanguage="json"
                    theme="vs-dark"
                    value={JSON.stringify(activeTemplate ?? { message: "Шаблон не выбран" }, null, 2)}
                    options={{ readOnly: true }}
                  />
                ) : (
                  <Textarea
                    className="mt-2"
                    value={JSON.stringify(activeTemplate ?? { message: "Шаблон не выбран" }, null, 2)}
                    minRows={8}
                    isReadOnly
                  />
                )}
              </AccordionItem>
            </Accordion>
          </CardBody>
        </Card>
      )}

      {activeTemplate && (
        <Card className="app-card">
          <CardHeader className="justify-between">
            <div>
              <div className="text-sm font-semibold text-[var(--app-text)]">Общие поля</div>
              <div className="text-xs text-muted">значения применяются поверх генерации</div>
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
                  {field.type === "json" && monacoOk ? (
                    <Accordion className="mt-2">
                      <AccordionItem key={`${field.key}-json`} title="JSON">
                        <Editor
                          height="200px"
                          defaultLanguage="json"
                          theme="vs-dark"
                          value={textValue as string}
                          onChange={(value) => {
                            const raw = value ?? "";
                            let val: unknown = raw;
                            try { val = JSON.parse(raw); } catch { val = raw; }
                            setGlobalOverride(stepId, name, val);
                          }}
                        />
                      </AccordionItem>
                    </Accordion>
                  ) : (
                    <Textarea
                      className="mt-2"
                      value={textValue as string}
                      minRows={2}
                      onChange={(e) => setGlobalOverride(stepId, name, e.target.value)}
                    />
                  )}
                </div>
              );
            })}
          </CardBody>
        </Card>
      )}
    </div>
  );
}

