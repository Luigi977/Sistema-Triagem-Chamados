
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

    @Column(length = 200)
    private String resumo;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(length = 20)
    private String natureza;

    @Column(length = 150)
    private String setor;

    @Column(length = 20)
    private String canal;

    @Column(length = 10)
    private String equipe;

    @Column(name = "solucao_aplicada", columnDefinition = "TEXT")
    private String solucaoAplicada;

    @Column(name = "responsavel_id")
    private Long responsavelId;
    public Long getResponsavelId() { return responsavelId; }
    public void setResponsavelId(Long id) { this.responsavelId = id; }

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

    public String getResumo() {
        return resumo;
    }

    public void setResumo(String resumo) {
        this.resumo = resumo;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public String getNatureza() {
        return natureza;
    }

    public void setNatureza(String natureza) {
        this.natureza = natureza;
    }

    public String getSetor() {
        return setor;
    }

    public void setSetor(String setor) {
        this.setor = setor;
    }

    public String getCanal() {
        return canal;
    }

    public void setCanal(String canal) {
        this.canal = canal;
    }

    public String getEquipe() {
        return equipe;
    }

    public void setEquipe(String equipe) {
        this.equipe = equipe;
    }

    public String getSolucaoAplicada() {
        return solucaoAplicada;
    }

    public void setSolucaoAplicada(String solucaoAplicada) {
        this.solucaoAplicada = solucaoAplicada;
    }
}
