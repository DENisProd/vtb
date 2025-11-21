package ru.poib.VTBHack.parser.model;

import lombok.Data;

@Data
public class ApiEndpointInfo {
    private String method; // GET, POST, PUT, DELETE
    private String path;   // /auth/bank-token
    private String description;
}
