import { ChevronLeft, ChevronRight } from 'lucide-react'
export function Pagination({
  page,
  totalPages,
  total,
  onChange,
}: {
  page: number
  totalPages: number
  total: number
  onChange: (page: number) => void
}) {
  return (
    <div className="pagination">
      <span>
        {total} {total === 1 ? 'chamado encontrado' : 'chamados encontrados'} nesta consulta
      </span>
      <div>
        <button
          className="icon-button"
          disabled={page <= 0}
          onClick={() => onChange(page - 1)}
          aria-label="Página anterior"
        >
          <ChevronLeft size={17} />
        </button>
        <span>
          Página {page + 1} de {Math.max(1, totalPages)}
        </span>
        <button
          className="icon-button"
          disabled={page + 1 >= totalPages}
          onClick={() => onChange(page + 1)}
          aria-label="Próxima página"
        >
          <ChevronRight size={17} />
        </button>
      </div>
    </div>
  )
}
