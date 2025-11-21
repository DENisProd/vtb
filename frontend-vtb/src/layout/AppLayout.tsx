import { Outlet } from "react-router-dom";

import { AppSidebar } from "@/components/layout/AppSidebar";
import { AppTopbar } from "@/components/layout/AppTopbar";

export const AppLayout = () => (
  <div className="app-shell flex h-screen w-screen overflow-hidden">
    <AppSidebar />
    <div className="flex flex-1 flex-col overflow-hidden">
      <AppTopbar />
      <main className="flex-1 overflow-y-auto px-4 py-6 md:px-8">
        <Outlet />
      </main>
    </div>
  </div>
);

