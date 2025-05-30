package com.example.mcp;

import org.springframework.stereotype.Service;

@Service
public class GptServiceOld {
    public String generateJcrQuery(String userQuestion) {
        // Stub implementation
        return "SELECT * FROM [cq:Page] AS s\n" +
                "WHERE \n" +
                "    ISDESCENDANTNODE(s, '/content/investsaudi/en')\n" +
                "    AND (\n" +
                "        s.[jcr:content/cq:lastReplicatedBy] = 'abaarmah@misa.gov.sa' \n" +
                " OR s.[jcr:content/cq:lastModifiedBy] = 'abaarmah@misa.gov.sa'\n" +
                "        OR s.[jcr:content/jcr:createdBy] = 'abaarmah@misa.gov.sa'\n" +
                "  \n" +
                "    )";
    }
}