package br.com.sistema.triagem.repository;

import br.com.sistema.triagem.model.entity.Chamado;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ChamadoRepository extends JpaRepository<Chamado, Long>, JpaSpecificationExecutor<Chamado> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Chamado c where c.id = :id")
    Optional<Chamado> findForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    Page<Chamado> findByStatusIn(Collection<String> statuses, Pageable pageable);

    Page<Chamado> findByTelefoneCliente(String telefoneCliente, Pageable pageable);

    Optional<Chamado> findFirstByTelefoneClienteOrderByDataAberturaDesc(String telefoneCliente);
}
