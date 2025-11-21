import { useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Button,
  Card,
  CardBody,
  CardHeader,
  Chip,
  Input,
  Radio,
  RadioGroup,
  Textarea,
} from "@heroui/react";
import {
  DocumentArrowUpIcon,
  FolderIcon,
  LinkIcon,
} from "@heroicons/react/24/outline";
import { useProjectStore } from "@/stores/project-store";

type CreationMode = "manual" | "repository" | "zip";

const NewProjectPage = () => {
  const navigate = useNavigate();
  const { createNewProject, loading } = useProjectStore();
  const [mode, setMode] = useState<CreationMode>("manual");
  const [name, setName] = useState("");
  const [bpmnXml, setBpmnXml] = useState("");
  const [openApiJson, setOpenApiJson] = useState("");
  const [pumlContent, setPumlContent] = useState("");
  const [error, setError] = useState<string | null>(null);

  const handleCreate = async () => {
    if (!name.trim()) {
      setError("Название проекта обязательно");
      return;
    }

    if (!bpmnXml.trim() && !openApiJson.trim()) {
      setError("Необходимо загрузить хотя бы BPMN или OpenAPI");
      return;
    }

    try {
      const project = await createNewProject({
        name: name.trim(),
        bpmnXml: bpmnXml.trim() || "",
        openApiJson: openApiJson.trim() || "",
        pumlContent: pumlContent.trim() || undefined,
      });
      navigate(`/projects/${project.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Не удалось создать проект");
    }
  };

  const handleFileUpload = (
    type: "bpmn" | "openapi" | "puml",
    event: React.ChangeEvent<HTMLInputElement>,
  ) => {
    const file = event.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (e) => {
      const content = e.target?.result as string;
      switch (type) {
        case "bpmn":
          setBpmnXml(content);
          break;
        case "openapi":
          setOpenApiJson(content);
          break;
        case "puml":
          setPumlContent(content);
          break;
      }
    };
    reader.readAsText(file);
  };

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <Card className="app-card">
        <CardHeader>
          <h1 className="text-2xl font-semibold text-[var(--app-text)]">
            Создание проекта
          </h1>
        </CardHeader>
        <CardBody className="space-y-6">
          <Input
            label="Название проекта"
            placeholder="Введите название проекта"
            value={name}
            onChange={(e) => setName(e.target.value)}
            isRequired
          />

          <div>
            <label className="text-sm font-medium text-[var(--app-text)] mb-2 block">
              Режим создания
            </label>
            <RadioGroup
              value={mode}
              onValueChange={(value) => setMode(value as CreationMode)}
            >
              <Radio value="manual">Вручную</Radio>
              <Radio value="repository">Импорт из репозитория</Radio>
              <Radio value="zip">Импорт zip-папки артефактов</Radio>
            </RadioGroup>
          </div>

          {mode === "manual" && (
            <div className="space-y-4">
              <div>
                <label className="text-sm font-medium text-[var(--app-text)] mb-2 block">
                  BPMN файл
                </label>
                <div className="flex gap-2">
                  <input
                    type="file"
                    accept=".bpmn,.xml"
                    onChange={(e) => handleFileUpload("bpmn", e)}
                    className="hidden"
                    id="bpmn-upload"
                  />
                  <label htmlFor="bpmn-upload">
                    <Button
                      as="span"
                      variant="bordered"
                      startContent={<DocumentArrowUpIcon className="w-4 h-4" />}
                    >
                      Загрузить BPMN
                    </Button>
                  </label>
                  {bpmnXml && (
                    <Chip color="success" size="sm">
                      Загружено
                    </Chip>
                  )}
                </div>
                {bpmnXml && (
                  <Textarea
                    className="mt-2"
                    label="BPMN содержимое"
                    value={bpmnXml}
                    onChange={(e) => setBpmnXml(e.target.value)}
                    minRows={5}
                  />
                )}
              </div>

              <div>
                <label className="text-sm font-medium text-[var(--app-text)] mb-2 block">
                  OpenAPI файл
                </label>
                <div className="flex gap-2">
                  <input
                    type="file"
                    accept=".json,.yaml,.yml"
                    onChange={(e) => handleFileUpload("openapi", e)}
                    className="hidden"
                    id="openapi-upload"
                  />
                  <label htmlFor="openapi-upload">
                    <Button
                      as="span"
                      variant="bordered"
                      startContent={<DocumentArrowUpIcon className="w-4 h-4" />}
                    >
                      Загрузить OpenAPI
                    </Button>
                  </label>
                  {openApiJson && (
                    <Chip color="success" size="sm">
                      Загружено
                    </Chip>
                  )}
                </div>
                {openApiJson && (
                  <Textarea
                    className="mt-2"
                    label="OpenAPI содержимое"
                    value={openApiJson}
                    onChange={(e) => setOpenApiJson(e.target.value)}
                    minRows={5}
                  />
                )}
              </div>

              <div>
                <label className="text-sm font-medium text-[var(--app-text)] mb-2 block">
                  PUML файл (опционально)
                </label>
                <div className="flex gap-2">
                  <input
                    type="file"
                    accept=".puml"
                    onChange={(e) => handleFileUpload("puml", e)}
                    className="hidden"
                    id="puml-upload"
                  />
                  <label htmlFor="puml-upload">
                    <Button
                      as="span"
                      variant="bordered"
                      startContent={<DocumentArrowUpIcon className="w-4 h-4" />}
                    >
                      Загрузить PUML
                    </Button>
                  </label>
                  {pumlContent && (
                    <Chip color="success" size="sm">
                      Загружено
                    </Chip>
                  )}
                </div>
                {pumlContent && (
                  <Textarea
                    className="mt-2"
                    label="PUML содержимое"
                    value={pumlContent}
                    onChange={(e) => setPumlContent(e.target.value)}
                    minRows={5}
                  />
                )}
              </div>
            </div>
          )}

          {mode === "repository" && (
            <div className="rounded-xl border border-white/10 p-4 bg-white/5">
              <p className="text-sm text-muted mb-4">
                Импорт из репозитория будет доступен в будущих версиях
              </p>
              <Input
                placeholder="https://github.com/company/project"
                startContent={<LinkIcon className="w-4 h-4" />}
                isDisabled
              />
            </div>
          )}

          {mode === "zip" && (
            <div className="rounded-xl border border-white/10 p-4 bg-white/5">
              <p className="text-sm text-muted mb-4">
                Импорт zip-папки будет доступен в будущих версиях
              </p>
              <input
                type="file"
                accept=".zip"
                className="hidden"
                id="zip-upload"
                disabled
              />
              <label htmlFor="zip-upload">
                <Button
                  as="span"
                  variant="bordered"
                  startContent={<FolderIcon className="w-4 h-4" />}
                  isDisabled
                >
                  Выбрать zip-файл
                </Button>
              </label>
            </div>
          )}

          {error && <div className="text-sm text-danger">{error}</div>}

          <div className="flex gap-2">
            <Button
              className="btn-primary"
              isLoading={loading}
              onPress={handleCreate}
            >
              Создать проект
            </Button>
            <Button variant="light" onPress={() => navigate(-1)}>
              Отмена
            </Button>
          </div>
        </CardBody>
      </Card>
    </div>
  );
};

export default NewProjectPage;

