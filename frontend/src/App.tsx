import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { AppLayout } from './components/layout/AppLayout';
import { Loading } from './components/common/Loading';
import { ProtectedRoute } from './components/layout/ProtectedRoute';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';

// The authenticated pages are code-split: each becomes its own chunk loaded on
// navigation, instead of everything shipping in one bundle. This is what keeps
// Recharts (used only by the dashboard) out of the initial download — the login
// screen no longer pays to download the charting library. The pages use named
// exports, so each import maps its name onto the default lazy() expects.
const DashboardPage = lazy(() => import('./pages/DashboardPage').then((m) => ({ default: m.DashboardPage })));
const VisionAreasPage = lazy(() => import('./pages/VisionAreasPage').then((m) => ({ default: m.VisionAreasPage })));
const DreamsPage = lazy(() => import('./pages/DreamsPage').then((m) => ({ default: m.DreamsPage })));
const DreamDetailPage = lazy(() => import('./pages/DreamDetailPage').then((m) => ({ default: m.DreamDetailPage })));
const GoalsPage = lazy(() => import('./pages/GoalsPage').then((m) => ({ default: m.GoalsPage })));
const StepsPage = lazy(() => import('./pages/StepsPage').then((m) => ({ default: m.StepsPage })));
const TasksBoardPage = lazy(() => import('./pages/TasksBoardPage').then((m) => ({ default: m.TasksBoardPage })));
const PartnersPage = lazy(() => import('./pages/PartnersPage').then((m) => ({ default: m.PartnersPage })));
const PartnerDetailPage = lazy(() => import('./pages/PartnerDetailPage').then((m) => ({ default: m.PartnerDetailPage })));
const ObstaclesPage = lazy(() => import('./pages/ObstaclesPage').then((m) => ({ default: m.ObstaclesPage })));
const CommunicationBuilderPage = lazy(() => import('./pages/CommunicationBuilderPage').then((m) => ({ default: m.CommunicationBuilderPage })));
const ReviewsPage = lazy(() => import('./pages/ReviewsPage').then((m) => ({ default: m.ReviewsPage })));
const InsightsPage = lazy(() => import('./pages/InsightsPage').then((m) => ({ default: m.InsightsPage })));
const ImportExportPage = lazy(() => import('./pages/ImportExportPage').then((m) => ({ default: m.ImportExportPage })));

export function App() {
  return (
    <Suspense fallback={<Loading />}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route element={<ProtectedRoute />}>
          <Route element={<AppLayout />}>
            <Route index element={<DashboardPage />} />
            <Route path="/vision-areas" element={<VisionAreasPage />} />
            <Route path="/dreams" element={<DreamsPage />} />
            <Route path="/dreams/:dreamId" element={<DreamDetailPage />} />
            <Route path="/vision-map" element={<DreamDetailPage />} />
            <Route path="/goals" element={<GoalsPage />} />
            <Route path="/steps" element={<StepsPage />} />
            <Route path="/tasks" element={<TasksBoardPage />} />
            <Route path="/partners" element={<PartnersPage />} />
            <Route path="/partners/:partnerId" element={<PartnerDetailPage />} />
            <Route path="/obstacles" element={<ObstaclesPage />} />
            <Route path="/communication" element={<CommunicationBuilderPage />} />
            <Route path="/reviews" element={<ReviewsPage />} />
            <Route path="/insights" element={<InsightsPage />} />
            <Route path="/import-export" element={<ImportExportPage />} />
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  );
}
