package br.com.sistema.triagem;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:triagem_test",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
class TriagemChamadosApplicationTests {

    @Test
    void contextLoads() {
    }
}