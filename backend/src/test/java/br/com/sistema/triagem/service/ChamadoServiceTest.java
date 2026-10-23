package br.com.sistema.triagem.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.sistema.triagem.model.entity.Chamado;
import br.com.sistema.triagem.dto.ChamadoRequests.CriarChamadoRequest;
import br.com.sistema.triagem.repository.ChamadoRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class ChamadoServiceTest {

    @Test
    void criarChamadoMantemPrioridadeSugeridaEApontaChamadoEncerradoAnterior() {
        ChamadoRepository repository = mock(ChamadoRepository.class);
        Chamado anterior = new Chamado();
        anterior.setStatus("ENCERRADO");
        ReflectionTestUtils.setField(anterior, "id", 42L);
        when(repository.findFirstByTelefoneClienteOrderByDataAberturaDesc("11999990000"))
                .thenReturn(Optional.of(anterior));
        when(repository.save(any(Chamado.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ChamadoService service = new ChamadoService(repository);

        Chamado chamado = service.criarChamado(new CriarChamadoRequest(
                "11999990000", "Cliente", "ALTA", null, null, null, null, null, null));

        assertEquals("ABERTO", chamado.getStatus());
        assertEquals("ALTA", chamado.getPrioridadeSugerida());
        assertEquals(42L, chamado.getChamadoAnteriorId());
        assertEquals("ENCERRADO", anterior.getStatus());
    }

    @Test
    void naoPermiteAlterarStatusDeChamadoEncerrado() {
        ChamadoRepository repository = mock(ChamadoRepository.class);
        Chamado encerrado = new Chamado();
        encerrado.setTelefoneCliente("11999990000");
        encerrado.setStatus("ENCERRADO");
        when(repository.findById(1L)).thenReturn(Optional.of(encerrado));
        ChamadoService service = new ChamadoService(repository);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.alterarStatus(1L, "EM_ATENDIMENTO"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }
}
