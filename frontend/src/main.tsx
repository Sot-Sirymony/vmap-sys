import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import { App } from './App';
import { AuthProvider } from './context/AuthContext';
import { ThemeModeProvider } from './context/ThemeModeContext';
import { ToastProvider } from './context/ToastContext';
import './styles/global.css';

// FR-39.6 changed this nesting: AuthProvider now wraps ThemeModeProvider,
// because the theme is loaded from the user's account and so needs the session
// token. ThemeModeProvider still sits above BrowserRouter and every page, so the
// MUI theme is in place for the login screen too — it just reads its values from
// localStorage until somebody signs in.
ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <AuthProvider>
      <ThemeModeProvider>
        <BrowserRouter>
          <ToastProvider>
            <App />
          </ToastProvider>
        </BrowserRouter>
      </ThemeModeProvider>
    </AuthProvider>
  </React.StrictMode>,
);
