package bank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import bank.infrastructure.projection.ProjectionRunner;

import java.time.Duration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AccountApiTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ProjectionRunner projections;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void commandsReturnAcceptedWithVersionAndQueryLocation() throws Exception {
        postJson("/accounts", "{\"accountId\":\"API-1\",\"owner\":\"Diego\"}")
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/accounts/API-1?minVersion=1"))
                .andExpect(jsonPath("$.version").value(1));

        postJson("/accounts/API-1/deposits", "{\"amount\":1000}")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.version").value(2));

        postJson("/accounts/API-1/withdrawals", "{\"amount\":300}")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.version").value(3));

        projections.awaitCatchUp(Duration.ofSeconds(2));

        mvc.perform(get("/accounts/API-1").param("minVersion", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(700))
                .andExpect(jsonPath("$.lastVersion").value(3));

        mvc.perform(get("/accounts/API-1/statement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[2].balanceAfter").value(700));

        mvc.perform(get("/accounts/API-1/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].type").value("MoneyDeposited"))
                .andExpect(jsonPath("$[1].data.amount").value(1000));
    }

    @Test
    void errorsAreMappedToHttpStatus() throws Exception {
        postJson("/accounts", "{\"accountId\":\"API-2\",\"owner\":\"Maria\"}").andExpect(status().isAccepted());

        postJson("/accounts", "{\"accountId\":\"API-2\",\"owner\":\"Outra\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_ALREADY_EXISTS"));

        postJson("/accounts/API-2/withdrawals", "{\"amount\":5000}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));

        postJson("/accounts/NOPE/deposits", "{\"amount\":10}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));

        postJson("/accounts/API-2/deposits", "{\"amount\":\"abc\"}")
                .andExpect(status().isBadRequest());

        postJson("/accounts/API-2/deposits", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void queryAskingForFutureVersionReturnsServiceUnavailable() throws Exception {
        postJson("/accounts", "{\"accountId\":\"API-3\",\"owner\":\"Joao\"}").andExpect(status().isAccepted());
        projections.awaitCatchUp(Duration.ofSeconds(2));

        mvc.perform(get("/accounts/API-3").param("minVersion", "99"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(jsonPath("$.code").value("READ_MODEL_BEHIND"));
    }

    private ResultActions postJson(String path, String body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
