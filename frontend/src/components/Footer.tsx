import React from 'react';
import { Database, Server, Layout, CheckCircle2 } from 'lucide-react';

export const Footer: React.FC = () => {
  return (
    <footer className="border-t border-slate-800/80 bg-slate-950 py-8 mt-auto">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex flex-col md:flex-row items-center justify-between gap-4 text-xs text-slate-500">
          <div className="flex items-center space-x-6">
            <span className="flex items-center gap-1.5">
              <Server className="h-3.5 w-3.5 text-indigo-400" />
              Spring Boot 3 (Java 21)
            </span>
            <span className="flex items-center gap-1.5">
              <Database className="h-3.5 w-3.5 text-blue-400" />
              PostgreSQL 16 & Flyway
            </span>
            <span className="flex items-center gap-1.5">
              <Layout className="h-3.5 w-3.5 text-cyan-400" />
              React 18 + Vite + TS
            </span>
          </div>

          <div className="flex items-center gap-2">
            <CheckCircle2 className="h-4 w-4 text-emerald-400" />
            <span>Phase 1 Architecture Scaffold Verified</span>
          </div>
        </div>
      </div>
    </footer>
  );
};
