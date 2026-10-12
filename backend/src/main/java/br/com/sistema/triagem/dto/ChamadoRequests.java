package br.com.sistema.triagem.dto;

public final class ChamadoRequests {

    private ChamadoRequests() {
    }

    public record CriarChamadoRequest(
            String telefoneCliente,
            String nomeCliente,
            String prioridadeSugerida) {
    }

    public record AlterarStatusRequest(String status) {
    }

    public record AlterarPrioridadeRequest(String prioridadeFinal) {
    }

    public record AtribuirResponsavelRequest(String responsavel) {
    }

    public record EncerrarChamadoRequest(String causaReal) {
    }
}
