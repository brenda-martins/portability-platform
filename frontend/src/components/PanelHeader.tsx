interface PanelHeaderProps {
  step: number;
  title: string;
  description: string;
}

export function PanelHeader({ step, title, description }: PanelHeaderProps) {
  return (
    <div className="panel-header">
      <span className="step" aria-hidden="true">
        {step}
      </span>
      <div>
        <h2>{title}</h2>
        <p className="panel-description">{description}</p>
      </div>
    </div>
  );
}
