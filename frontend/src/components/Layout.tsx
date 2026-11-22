import type { ReactNode } from 'react';

interface LayoutProps {
  children: ReactNode;
}

export function Layout({ children }: LayoutProps) {
  return (
    <div className="app">
      <header className="app-header">
        <p className="app-eyebrow">Fluxo de portabilidade</p>
        <h1>Portability Platform</h1>
        <p>Portabilidade de convênios de desconto em folha</p>
      </header>
      <main className="app-main">{children}</main>
    </div>
  );
}
