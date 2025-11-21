package ru.poib.VTBHack.parser.model.puml;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiCallInfo {
    private boolean isRequest;              // true = request, false = response
    private String method;                  // GET, POST, PUT, DELETE, PATCH
    private String path;                    // /auth/token, /accounts, etc.
    private List<String> parameters;        // client_id, client_secret, etc.
    private String responseDescription;     // Для response: "access_token", "list of accounts"

    public boolean isResponse() {
        return !isRequest;
    }
}
