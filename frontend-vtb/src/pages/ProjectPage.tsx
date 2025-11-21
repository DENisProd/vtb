import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Button, Chip, Tab, Tabs } from "@heroui/react";
import { ArrowLeftIcon, BoltIcon } from "@heroicons/react/24/outline";
import { getProject, requestMapping, type ProjectDto } from "@/lib/testflow-api";
import { ProjectContextPanel } from "@/components/projects/ProjectContextPanel";
import { ProjectArtifactsTab } from "@/components/projects/ProjectArtifactsTab";
import { ProjectAnalysisTab } from "@/components/projects/ProjectAnalysisTab";
import { ProjectChainTab } from "@/components/projects/ProjectChainTab";
import { ProjectRunnerTab } from "@/components/projects/ProjectRunnerTab";
import { ProjectScenariosTab } from "@/components/projects/ProjectScenariosTab";
import { ProjectTestDataTab } from "@/components/projects/ProjectTestDataTab";
import { ProjectOverviewTab } from "@/components/projects/ProjectOverviewTab";

const ProjectPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [project, setProject] = useState<ProjectDto | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<string>("overview");

  useEffect(() => {
    if (!id) return;
    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        const p = await getProject(id);
        setProject(p);
      } catch (e) {
        setError(e instanceof Error ? e.message : "Не удалось загрузить проект");
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [id]);

  if (!project) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="text-center">
          {error ? (
            <>
              <div className="text-muted mb-4">{error}</div>
              <Button variant="light" onPress={() => navigate("/projects")}>
                Вернуться к проектам
              </Button>
            </>
          ) : (
            <div className="text-muted">Загрузка проекта…</div>
          )}
        </div>
      </div>
    );
  }

  const mappingResult = project.mappingResult;
  const hasErrors = mappingResult && (
    (mappingResult.unmatchedTasks?.length ?? 0) > 0 ||
    (mappingResult.aiVerificationReport?.openapi?.errors?.length ?? 0) > 0 ||
    (mappingResult.aiVerificationReport?.bpmn?.errors?.length ?? 0) > 0
  );
  const errorCount = mappingResult ? (
    (mappingResult.unmatchedTasks?.length ?? 0) +
    (mappingResult.aiVerificationReport?.openapi?.errors?.length ?? 0) +
    (mappingResult.aiVerificationReport?.bpmn?.errors?.length ?? 0)
  ) : 0;

  return (
    <div className="flex flex-col h-full gap-4 min-h-0">
      {/* Заголовок - фиксированный */}
      <div className="flex items-center justify-between shrink-0 pb-2 border-b border-white/10">
        <div className="flex items-center gap-3">
          <Button
            isIconOnly
            variant="light"
            size="sm"
            onPress={() => navigate("/projects")}
          >
            <ArrowLeftIcon className="w-5 h-5" />
          </Button>
          <div>
            <h1 className="text-2xl font-bold text-[var(--app-text)]">
              {project.name}
            </h1>
            <div className="text-xs text-muted mt-0.5">ID: {project.id}</div>
          </div>
        </div>
        <div className="flex items-center gap-3">
          {loading && <Chip size="sm" variant="flat">Обновление…</Chip>}
          {mappingResult && (
            <Chip
              size="sm"
              variant="flat"
              color={hasErrors ? "danger" : "success"}
            >
              {hasErrors ? `${errorCount} ошибок` : "Анализ пройден"}
            </Chip>
          )}
          <Button
            className="btn-primary"
            size="sm"
            startContent={<BoltIcon className="w-4 h-4" />}
            isLoading={loading}
            onPress={async () => {
              const hasOpenApi = !!project.openApiJson;
              const hasProcess = !!project.bpmnXml || !!project.pumlContent;
              if (!hasOpenApi || !hasProcess) {
                setTab("artifacts");
                return;
              }
              try {
                setLoading(true);
                const mapping = await requestMapping({ bpmnXml: project.bpmnXml, openApiJson: project.openApiJson } as any);
                setProject({ ...project, mappingResult: mapping });
                setTab("analysis");
              } catch (e) {
                setError(e instanceof Error ? e.message : "Не удалось выполнить анализ");
              } finally {
                setLoading(false);
              }
            }}
          >
            Выполнить анализ
          </Button>
        </div>
      </div>

      {/* Основной контент - flex с правильным скроллом */}
      <div className="flex flex-col lg:flex-row gap-4 flex-1 min-h-0">
        {/* Центральная рабочая зона */}
        <div className="flex-1 flex flex-col min-w-0 min-h-0">
          {/* Вкладки */}
          <Tabs
            aria-label="Project tabs"
            selectedKey={tab}
            onSelectionChange={(key) => setTab(String(key))}
            classNames={{
              base: "shrink-0",
              tabList: "gap-2",
              tab: "min-w-0 px-4",
            }}
          >
            <Tab 
              key="overview" 
              title={
                <span className="flex items-center gap-2">
                  Обзор
                </span>
              } 
            />
            <Tab key="artifacts" title="Артефакты" />
            <Tab 
              key="analysis" 
              title={
                <span className="flex items-center gap-2">
                  Анализ
                  {hasErrors && (
                    <Chip size="sm" variant="flat" color="danger">{errorCount}</Chip>
                  )}
                </span>
              } 
            />
            <Tab key="chain" title="Цепочка вызовов" />
            <Tab key="data" title="Тестовые данные" />
            <Tab key="scenarios" title="Сценарии" />
            <Tab key="runner" title="Прогон" />
          </Tabs>

          {/* Содержимое вкладок - скроллируемое */}
          <div className="flex-1 min-h-0 min-w-0 break-words mt-4">
            {tab === "overview" && (
              <ProjectOverviewTab project={project} />
            )}
            {tab === "artifacts" && (
              <ProjectArtifactsTab project={project} requireHint={!project.openApiJson || (!project.bpmnXml && !project.pumlContent)} />
            )}
            {tab === "analysis" && (
              <ProjectAnalysisTab project={project} />
            )}
            {tab === "chain" && (
              <ProjectChainTab project={project} />
            )}
            {tab === "data" && (
              <ProjectTestDataTab project={project} />
            )}
            {tab === "scenarios" && (
              <ProjectScenariosTab project={project} />
            )}
            {tab === "runner" && (
              <ProjectRunnerTab project={project} />
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default ProjectPage;