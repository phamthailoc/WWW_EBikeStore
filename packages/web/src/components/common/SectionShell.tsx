import type { PropsWithChildren } from "react";

interface SectionShellProps extends PropsWithChildren {
  eyebrow: string;
  title: string;
  description: string;
}

const SectionShell = ({ eyebrow, title, description, children }: SectionShellProps) => (
  <section className="min-h-screen">
    <div className="px-0 py-6">
      <span className="mb-3 inline-block text-xs font-bold uppercase tracking-widest text-primary">{eyebrow}</span>
      <h1 className="mb-3 text-3xl font-bold leading-tight md:text-4xl">{title}</h1>
      <p className="max-w-2xl text-base leading-7 text-gray-600">{description}</p>
    </div>
    {children}
  </section>
);

export default SectionShell;
