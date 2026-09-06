/** Un icono es una lista de trazos sobre 24x24, como en el artboard. */
export type Trazos = readonly string[];

export function Icono({
  d,
  tam = 16,
  grosor = 1.8,
}: {
  d: Trazos;
  tam?: number;
  grosor?: number;
}) {
  return (
    <svg
      width={tam}
      height={tam}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={grosor}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      style={{ flex: '0 0 auto' }}
    >
      {d.map((p) => (
        <path key={p} d={p} />
      ))}
    </svg>
  );
}
