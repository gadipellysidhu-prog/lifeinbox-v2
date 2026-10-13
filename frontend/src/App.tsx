import React from 'react';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from './context/AuthContext';
import { AppRoutes } from './routes/AppRoutes';
import { Header } from './components/Header';
import { Footer } from './components/Footer';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 10,
      refetchOnWindowFocus: false,
    },
  },
});

export interface AppProps {
  basename?: string;
}

export const App: React.FC<AppProps> = ({ basename: customBasename }) => {
  const rawBase = import.meta.env.BASE_URL || '/';
  const defaultBasename = rawBase.replace(/\/$/, '') || '/';
  const effectiveBasename =
    customBasename ??
    (typeof window !== 'undefined' &&
    defaultBasename !== '/' &&
    !window.location.pathname.startsWith(defaultBasename)
      ? '/'
      : defaultBasename);

  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter basename={effectiveBasename}>
        <AuthProvider>
          <div className="min-h-screen flex flex-col bg-slate-950 text-slate-100 selection:bg-indigo-500 selection:text-white">
            <Header />
            <main className="flex-1">
              <AppRoutes />
            </main>
            <Footer />
          </div>
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  );
};

export default App;
