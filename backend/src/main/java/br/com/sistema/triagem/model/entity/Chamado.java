
package br.com.sistema.triagem.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chamados")
public class Chamado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telefone_cliente", nullable = false, length = 30)
    private String telefoneCliente;

    @Column(name = "chamado_anterior_id")
    private Long chamadoAnteriorId;

    @Column(name = "nome_cliente", length = 150)
    private String nomeCliente;

    @Column(nullable = false, length = 30)
    private String status = "ABERTO";

    @Column(name = "prioridade_sugerida", length = 20)
    private String prioridadeSugerida;

    @Column(name = "prioridade_final", length = 20)
    private String prioridadeFinal;

    @Column(length = 150)
    private String responsavel;

    @Column(name = "causa_real", columnDefinition = "TEXT")
    private String causaReal;

    @Column(name = "data_abertura", nullable = false)
    private LocalDateTime dataAbertura = LocalDateTime.now();

    @Column(name = "data_encerramento")
    private LocalDateTime dataEncerramento;

    public Chamado() {
    }

    public Long getId() {
        return id;
    }

    public String getTelefoneCliente() {
        return telefoneCliente;
    }

    public void setTelefoneCliente(String telefoneCliente) {
        this.telefoneCliente = telefoneCliente;
    }

    public Long getChamadoAnteriorId() {
        return chamadoAnteriorId;
    }

    public void setChamadoAnteriorId(Long chamadoAnteriorId) {
        this.chamadoAnteriorId = chamadoAnteriorId;
    }

    public String getNomeCliente() {
        return nomeCliente;
    }

    public void setNomeCliente(String nomeCliente) {
        this.nomeCliente = nomeCliente;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPrioridadeSugerida() {
        return prioridadeSugerida;
    }

    public void setPrioridadeSugerida(String prioridadeSugerida) {
        this.prioridadeSugerida = prioridadeSugerida;
    }

    public String getPrioridadeFinal() {
        return prioridadeFinal;
    }

    public void setPrioridadeFinal(String prioridadeFinal) {
        this.prioridadeFinal = prioridadeFinal;
    }

    public String getResponsavel() {
        return responsavel;
    }

    public void setResponsavel(String responsavel) {
        this.responsavel = responsavel;
    }

    public String getCausaReal() {
        return causaReal;
    }

    public void setCausaReal(String causaReal) {
        this.causaReal = causaReal;
    }

    public LocalDateTime getDataAbertura() {
        return dataAbertura;
    }

    public void setDataAbertura(LocalDateTime dataAbertura) {
        this.dataAbertura = dataAbertura;
    }

    public LocalDateTime getDataEncerramento() {
        return dataEncerramento;
    }

    public void setDataEncerramento(LocalDateTime dataEncerramento) {
        this.dataEncerramento = dataEncerramento;
    }
}
