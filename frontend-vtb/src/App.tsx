import { Navigate, Route, Routes } from "react-router-dom";

import { AppLayout } from "@/layout/AppLayout";
import AnalysisPage from "@/pages/AnalysisPage";
import ApiTestPage from "@/pages/ApiTestPage";
import ArtifactsPage from "@/pages/ArtifactsPage";
import CanvasPage from "@/pages/CanvasPage";
import DataGeneratorPage from "@/pages/DataGeneratorPage";
import DashboardPage from "@/pages/DashboardPage";
import ProjectsPage from "@/pages/ProjectsPage";
import ProjectPage from "@/pages/ProjectPage";
import ReportsPage from "@/pages/ReportsPage";
import RunnerPage from "@/pages/RunnerPage";
import ScenariosPage from "@/pages/ScenariosPage";
import SettingsPage from "@/pages/SettingsPage";

const App = () => (
  <Routes>
    <Route element={<AppLayout />}>
      <Route element={<DashboardPage />} path="/" />
      <Route element={<ProjectsPage />} path="/projects" />
      <Route element={<ProjectPage />} path="/projects/:id" />
      <Route element={<ArtifactsPage />} path="/artifacts" />
      <Route element={<AnalysisPage />} path="/analysis" />
      <Route element={<ScenariosPage />} path="/scenarios" />
      <Route element={<DataGeneratorPage />} path="/data" />
      <Route element={<ApiTestPage />} path="/api-test" />
      <Route element={<RunnerPage />} path="/runner" />
      <Route element={<CanvasPage />} path="/canvas" />
      <Route element={<ReportsPage />} path="/reports" />
      <Route element={<SettingsPage />} path="/settings" />
    </Route>
    <Route element={<Navigate replace to="/" />} path="*" />
  </Routes>
);

export default App;

