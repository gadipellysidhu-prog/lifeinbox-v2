import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { fetchHealthStatus } from '../api/health';
import {
  Activity,
  Layers,
  Database,
  Lock,
  Calendar,
  CheckCircle2,
  Clock,
  Tag,
  AlertCircle,
  RefreshCw,
  FolderGit2
} from 'lucide-react';

export const HomePage: React.FC = () => {
  const { data: health, isLoading, isError, refetch, isFetching } = useQuery({
    queryKey: ['backend-health'],
    queryFn: fetchHealthStatus,
    retry: 2,
    refetchInterval: 15000,
  });

  return (
    <div className="min-h-full py-8 px-4 sm:px-6 lg:px-8 max-w-7xl mx-auto space-y-8">
      {/* Hero Banner */}
      <div className="relative overflow-hidden rounded-2xl bg-gradient-to-br from-indigo-950/50 via-slate-900 to-slate-950 border border-slate-800 p-8 sm:p-10 shadow-2xl">
        <div className="absolute top-0 right-0 -mr-16 -mt-16 w-64 h-64 bg-indigo-500/10 rounded-full blur-3xl pointer-events-none" />
        <div className="relative z-10 max-w-3xl space-y-4">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full text-xs font-semibold bg-indigo-500/10 text-indigo-300 border border-indigo-500/20">
            <FolderGit2 className="h-3.5 w-3.5" />
            Monorepo Scaffold Initialized
          </div>
          <h1 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white">
            Welcome to <span className="bg-gradient-to-r from-indigo-400 via-purple-300 to-pink-400 bg-clip-text text-transparent">LifeInbox</span>
          </h1>
          <p className="text-base sm:text-lg text-slate-300 leading-relaxed">
            Your centralized intelligence hub for capturing, prioritizing, and organizing life reminders.
            Infrastructure scaffolding is operational and ready for Phase 2 implementation.
          </p>
        </div>
      </div>

      {/* Backend Live Status Card */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div className="col-span-1 md:col-span-2 rounded-xl bg-slate-900/60 border border-slate-800 p-6 flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2.5">
                <div className="p-2 rounded-lg bg-indigo-950/70 border border-indigo-800/40 text-indigo-400">
                  <Activity className="h-5 w-5" />
                </div>
                <div>
                  <h2 className="text-sm font-semibold text-white">Backend Live Liveness Status</h2>
                  <p className="text-xs text-slate-400">Communicates with Spring Boot 3 API</p>
                </div>
              </div>
              <button
                onClick={() => refetch()}
                disabled={isFetching}
                className="p-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 transition-colors disabled:opacity-50"
                title="Refresh Status"
              >
                <RefreshCw className={`h-4 w-4 ${isFetching ? 'animate-spin text-indigo-400' : ''}`} />
              </button>
            </div>

            <div className="mt-6">
              {isLoading ? (
                <div className="flex items-center gap-3 text-slate-400 text-sm">
                  <div className="h-4 w-4 border-2 border-indigo-500 border-t-transparent rounded-full animate-spin" />
                  <span>Checking backend health at /api/health...</span>
                </div>
              ) : isError ? (
                <div className="flex items-center gap-3 p-3.5 rounded-lg bg-rose-950/40 border border-rose-800/50 text-rose-300 text-sm">
                  <AlertCircle className="h-5 w-5 flex-shrink-0 text-rose-400" />
                  <div>
                    <p className="font-semibold">Backend Unreachable</p>
                    <p className="text-xs text-rose-400/80">
                      Ensure Spring Boot backend is running at http://localhost:8080.
                    </p>
                  </div>
                </div>
              ) : (
                <div className="space-y-3">
                  <div className="flex items-center gap-3 p-3.5 rounded-lg bg-emerald-950/40 border border-emerald-800/50 text-emerald-300 text-sm">
                    <CheckCircle2 className="h-5 w-5 flex-shrink-0 text-emerald-400" />
                    <div>
                      <span className="font-semibold">Status: </span>
                      <span className="font-mono bg-emerald-900/60 px-2 py-0.5 rounded text-xs font-bold text-emerald-200">
                        {health?.status}
                      </span>
                      <span className="mx-2 text-slate-500">•</span>
                      <span className="font-semibold">Service: </span>
                      <span className="font-mono text-xs">{health?.service}</span>
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>

          <div className="mt-6 pt-4 border-t border-slate-800/80 flex items-center justify-between text-xs text-slate-400">
            <span>Endpoint: <code className="text-indigo-400">GET /api/health</code></span>
            <span>Target Port: <code className="text-indigo-400">8080</code></span>
          </div>
        </div>

        {/* Database & Schema Status */}
        <div className="rounded-xl bg-slate-900/60 border border-slate-800 p-6 flex flex-col justify-between">
          <div className="space-y-4">
            <div className="flex items-center gap-2.5">
              <div className="p-2 rounded-lg bg-blue-950/70 border border-blue-800/40 text-blue-400">
                <Database className="h-5 w-5" />
              </div>
              <div>
                <h2 className="text-sm font-semibold text-white">Database & Migration</h2>
                <p className="text-xs text-slate-400">PostgreSQL 16 + Flyway</p>
              </div>
            </div>

            <ul className="space-y-2.5 text-xs text-slate-300">
              <li className="flex items-center justify-between py-1 border-b border-slate-800/60">
                <span className="text-slate-400">Database Engine</span>
                <span className="font-mono text-slate-200">PostgreSQL 16</span>
              </li>
              <li className="flex items-center justify-between py-1 border-b border-slate-800/60">
                <span className="text-slate-400">Flyway Version</span>
                <span className="font-mono text-emerald-400">V1__create_initial_schema</span>
              </li>
              <li className="flex items-center justify-between py-1 border-b border-slate-800/60">
                <span className="text-slate-400">Primary Key Standard</span>
                <span className="font-mono text-indigo-400">UUID (gen_random_uuid)</span>
              </li>
              <li className="flex items-center justify-between py-1">
                <span className="text-slate-400">Schema Tables</span>
                <span className="font-mono text-slate-200">users, reminders</span>
              </li>
            </ul>
          </div>

          <div className="mt-4 pt-3 border-t border-slate-800/80 text-xs text-slate-400">
            <span>DDL Mode: <code className="text-blue-400">validate</code></span>
          </div>
        </div>
      </div>

      {/* Inbox Placeholder & Architecture Preview */}
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-bold text-white flex items-center gap-2">
            <Layers className="h-5 w-5 text-indigo-400" />
            Inbox Preview (Architecture Spec Placeholder)
          </h2>
          <span className="text-xs text-slate-400 bg-slate-900 px-3 py-1 rounded-full border border-slate-800">
            Phase 2 Scope: Auth & Reminder CRUD
          </span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="p-5 rounded-xl bg-slate-900/40 border border-slate-800/80 hover:border-slate-700 transition-colors space-y-3">
            <div className="flex items-start justify-between">
              <span className="inline-flex items-center gap-1 text-[11px] font-semibold px-2 py-0.5 rounded bg-rose-950/70 text-rose-300 border border-rose-800/40">
                HIGH PRIORITY
              </span>
              <span className="text-xs text-slate-400 flex items-center gap-1">
                <Clock className="h-3 w-3 text-slate-500" />
                Tomorrow, 9:00 AM
              </span>
            </div>
            <h3 className="text-sm font-semibold text-white">Review Q4 Product Roadmap & Goals</h3>
            <p className="text-xs text-slate-400 leading-relaxed">
              Consolidate team milestones and prepare sync deck for Monday executive briefing.
            </p>
            <div className="flex items-center justify-between pt-2 text-xs text-slate-500 border-t border-slate-800/60">
              <span className="flex items-center gap-1 text-slate-400">
                <Tag className="h-3 w-3 text-indigo-400" /> Work
              </span>
              <span className="text-amber-400 font-medium">PENDING</span>
            </div>
          </div>

          <div className="p-5 rounded-xl bg-slate-900/40 border border-slate-800/80 hover:border-slate-700 transition-colors space-y-3">
            <div className="flex items-start justify-between">
              <span className="inline-flex items-center gap-1 text-[11px] font-semibold px-2 py-0.5 rounded bg-amber-950/70 text-amber-300 border border-amber-800/40">
                MEDIUM PRIORITY
              </span>
              <span className="text-xs text-slate-400 flex items-center gap-1">
                <Clock className="h-3 w-3 text-slate-500" />
                Oct 15, 2:00 PM
              </span>
            </div>
            <h3 className="text-sm font-semibold text-white">Annual Health Checkup & Lab Work</h3>
            <p className="text-xs text-slate-400 leading-relaxed">
              Appointment at St. Jude Medical Clinic. Remember to fast 10 hours prior.
            </p>
            <div className="flex items-center justify-between pt-2 text-xs text-slate-500 border-t border-slate-800/60">
              <span className="flex items-center gap-1 text-slate-400">
                <Tag className="h-3 w-3 text-indigo-400" /> Personal
              </span>
              <span className="text-amber-400 font-medium">PENDING</span>
            </div>
          </div>

          <div className="p-5 rounded-xl bg-slate-900/40 border border-slate-800/80 hover:border-slate-700 transition-colors space-y-3">
            <div className="flex items-start justify-between">
              <span className="inline-flex items-center gap-1 text-[11px] font-semibold px-2 py-0.5 rounded bg-blue-950/70 text-blue-300 border border-blue-800/40">
                LOW PRIORITY
              </span>
              <span className="text-xs text-slate-400 flex items-center gap-1">
                <Calendar className="h-3 w-3 text-slate-500" />
                Weekend
              </span>
            </div>
            <h3 className="text-sm font-semibold text-white">Car Maintenance & Tire Rotation</h3>
            <p className="text-xs text-slate-400 leading-relaxed">
              Schedule quick service inspection before upcoming road trip.
            </p>
            <div className="flex items-center justify-between pt-2 text-xs text-slate-500 border-t border-slate-800/60">
              <span className="flex items-center gap-1 text-slate-400">
                <Tag className="h-3 w-3 text-indigo-400" /> Home & Auto
              </span>
              <span className="text-slate-400 font-medium">PENDING</span>
            </div>
          </div>
        </div>
      </div>

      {/* Security and Architecture Note */}
      <div className="rounded-xl bg-slate-900/40 border border-slate-800 p-5 flex items-start gap-3">
        <Lock className="h-5 w-5 text-indigo-400 flex-shrink-0 mt-0.5" />
        <div className="text-xs text-slate-300 space-y-1">
          <p className="font-semibold text-slate-200">Phase 1 Security & Access Architecture</p>
          <p className="text-slate-400 leading-relaxed">
            Stateless SecurityFilterChain with public access to <code className="text-indigo-300">/api/health</code> and Swagger UI endpoints. All business domains are secured and ready for JWT authentication in Phase 2.
          </p>
        </div>
      </div>
    </div>
  );
};
