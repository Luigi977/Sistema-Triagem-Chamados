package br.com.sistema.triagem.service;

import br.com.sistema.triagem.client.EvolutionApiClient;
import br.com.sistema.triagem.config.EvolutionApiConfig.Properties;
import br.com.sistema.triagem.dto.MensagemResponseDTO;
import br.com.sistema.triagem.dto.evolution.ResponderWhatsAppDTO;
import br.com.sistema.triagem.repository.ChamadoRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
public class WhatsAppService {
    private final EvolutionApiClient client;
    private final EvolutionStore store;
    private final ChamadoRepository chamados;
    private final MensagemService mensagens;
    private final AcessoService acesso;
    private final Properties config;

    public WhatsAppService(EvolutionApiClient client, EvolutionStore store, ChamadoRepository chamados,
                           MensagemService mensagens, AcessoService acesso, Properties config) {
        this.client=client; this.store=store; this.chamados=chamados; this.mensagens=mensagens;
        this.acesso=acesso; this.config=config;
    }

    public EvolutionApiClient.Status status() { return client.status(); }

    public EvolutionApiClient.Conexao conectar() {
        acesso.admin();
        return client.conectar();
    }

    @Transactional
    public MensagemResponseDTO responder(Long id, ResponderWhatsAppDTO request) {
        if (request == null || request.texto() == null || request.texto().isBlank() || request.texto().length() > 10000)
            throw new ResponseStatusException(BAD_REQUEST, "texto é obrigatório e deve ter até 10000 caracteres");
        client.exigirHabilitado();
        // O worker adquire o mesmo lock: um eco antecipado espera o registro do envio.
        store.coordenar();
        var chamado = chamados.findForUpdate(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        acesso.editar(chamado);
        if ("ENCERRADO".equals(chamado.getStatus())) throw new ResponseStatusException(CONFLICT, "Chamado encerrado");
        String destinatario;
        if (chamado.getContatoWhatsappId() != null) destinatario = store.porId(chamado.getContatoWhatsappId()).jid();
        else {
            String telefone = chamado.getTelefoneCliente();
            if (telefone == null || !telefone.matches("[0-9]{7,15}"))
                throw new ResponseStatusException(CONFLICT, "Chamado sem destinatário WhatsApp válido com DDI");
            destinatario = telefone + "@s.whatsapp.net";
            var contato = store.identificar(destinatario, null);
            chamado.setContatoWhatsappId(contato.id());
        }
        var enviado = client.enviarTexto(destinatario, request.texto());
        var mensagem = mensagens.registrarWhatsapp(chamado, "TECNICO", request.texto(), "TEXTO", LocalDateTime.now(),
                WebhookService.externo(config.instance(), enviado.id()), acesso.atual().id());
        acesso.registrar(chamado, "RESPOSTA_WHATSAPP");
        return mensagem;
    }
}
