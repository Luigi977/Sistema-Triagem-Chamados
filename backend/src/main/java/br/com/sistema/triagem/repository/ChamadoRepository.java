package br.com.sistema.triagem.repository;

import br.com.sistema.triagem.model.entity.Chamado;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ChamadoRepository extends JpaRepository<Chamado, Long>, JpaSpecificationExecutor<Chamado> {

    Page<Chamado> findByStatusIn(Collection<String> statuses, Pageable pageable);

    Page<Chamado> findByTelefoneCliente(String telefoneCliente, Pageable pageable);

    Optional<Chamado> findFirstByTelefoneClienteOrderByDataAberturaDesc(String telefoneCliente);
}
