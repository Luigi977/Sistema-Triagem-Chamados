package br.com.sistema.triagem.dto;

public final class ChamadoRequests {

    private ChamadoRequests() {
    }

    public record CriarChamadoRequest(
            String telefoneCliente,
            String nomeCliente,
            String prioridadeSugerida,
            String resumo,
            String descricao,
            String natureza,
            String setor,
            String canal,
            String equipe) {
    }

    public record AlterarStatusRequest(String status) {
    }

    public record AlterarPrioridadeRequest(String prioridadeFinal) {
    }

    public record AtribuirResponsavelRequest(Long responsavelId) {
    }

    public record AtualizarDetalhesRequest(
            String resumo, String descricao, String natureza, String setor, String canal) {
    }

    public record AlterarEquipeRequest(String equipe) {
    }

    public record EncerrarChamadoRequest(String causaReal, String solucaoAplicada) {
    }
}
