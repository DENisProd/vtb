import { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import {
  Button,
  Chip,
  Tab,
  Tabs,
} from "@heroui/react";
import {
  ArrowLeftIcon,
  BoltIcon,
} from "@heroicons/react/24/outline";
import { useProjectStore } from "@/stores/project-store";
import { ProjectArtifactsTab } from "@/components/projects/ProjectArtifactsTab";
import { ProjectAnalysisTab } from "@/components/projects/ProjectAnalysisTab";
import { ProjectChainTab } from "@/components/projects/ProjectChainTab";
import { ProjectTestDataTab } from "@/components/projects/ProjectTestDataTab";
import { ProjectScenariosTab } from "@/components/projects/ProjectScenariosTab";
import RunnerPage from "@/pages/RunnerPage";
import { ProjectOverviewTab } from "@/components/projects/ProjectOverviewTab";
import { ProjectContextPanel } from "@/components/projects/ProjectContextPanel";

const ProjectDetailPage = () => {
  const params = useParams();
  const navigate = useNavigate();
  const projectId = params.id as string;
  const { getSelectedProject, refreshProject, runAnalysis, loading } =
    useProjectStore();
  const [selectedTab, setSelectedTab] = useState("overview");

  const project = getSelectedProject();
  const mappingResult = project?.mappingResult;
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

  useEffect(() => {
    if (projectId) {
      useProjectStore.getState().selectProject(projectId);
      refreshProject(projectId);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId]);

  if (!project) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="text-center">
          <div className="text-muted mb-4">Проект не найден</div>
          <Button
            variant="light"
            onPress={() => navigate("/projects")}
          >
            Вернуться к проектам
          </Button>
        </div>
      </div>
    );
  }

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
            onPress={() => runAnalysis(project.id)}
          >
            Выполнить анализ
          </Button>
        </div>
      </div>

      {/* Основной контент - flex с правильным скроллом */}
      <div className="flex gap-4 flex-1 min-h-0">
        {/* Центральная рабочая зона */}
        <div className="flex-1 flex flex-col min-w-0 min-h-0">
          {/* Вкладки */}
          <Tabs
            aria-label="Project tabs"
            selectedKey={selectedTab}
            onSelectionChange={(key) => setSelectedTab(key as string)}
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
                  {!mappingResult && (
                    <Chip size="sm" variant="flat" color="default">Новый</Chip>
                  )}
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
            <Tab key="test-data" title="Тестовые данные" />
            <Tab key="scenarios" title="Сценарии" />
            <Tab key="runner" title="Прогон" />
          </Tabs>

          {/* Содержимое вкладок - скроллируемое */}
          <div className="flex-1 min-h-0 overflow-y-auto mt-4">
            {selectedTab === "overview" && (
              <ProjectOverviewTab project={project} />
            )}
            {selectedTab === "artifacts" && (
              <ProjectArtifactsTab project={project} />
            )}
            {selectedTab === "analysis" && (
              <ProjectAnalysisTab project={project} />
            )}
            {selectedTab === "chain" && (
              <ProjectChainTab project={project} />
            )}
            {selectedTab === "test-data" && (
              <ProjectTestDataTab project={project} />
            )}
            {selectedTab === "scenarios" && (
              <ProjectScenariosTab project={project} />
            )}
            {selectedTab === "runner" && <RunnerPage />}
          </div>
        </div>

        {/* Правая контекстная панель - компактная */}
        <ProjectContextPanel project={project} />
      </div>
    </div>
  );
};

export default ProjectDetailPage;

