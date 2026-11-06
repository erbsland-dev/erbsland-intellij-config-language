package dev.erbsland.elcl.style

/** Separate, valid documents make each tab's effects visible without unrelated examples. */
internal object ElclStyleExamples {
    val layout = """
        [server]
        port:8080
        connection_timeout:30s
        welcome_message:"Welcome to the example server. This message fits on a continuation line at the default width."
        long_message:"This deliberately long message demonstrates wrapping when a complete value is still too wide on its own continuation line; literals are never split."
        next_line:
            "This value starts on the next line."
        banner:
            TRIPLE_QUOTE
            Welcome to the server.
                Extra payload indentation is preserved.
            TRIPLE_QUOTE
    """.trimIndent().replace("TRIPLE_QUOTE", "\"\"\"") + "\n"

    val names = """
        [server_settings]
        display_name: "primary_server"
        connection_type: "secure_socket"
        fallback_name: "backup_server"

        [client_settings]
        retry_count: 3

        [logging_settings]
        output_path: "logs/server.txt"

        [labels]
        "display_name": "Primary server"
        "status_label": "Ready"
        "readme.txt": "Punctuation is preserved."
    """.trimIndent() + "\n"

    val sections = """
        [app.server]
        port: 8080

        [.tls]
        enabled: yes

        *[upstream_servers]
        name: "Primary"

        *[upstream_servers]
        name: "Backup"

        [app.logging]
        enabled: yes
    """.trimIndent() + "\n"

    val lists = """
        [lists]
        ports: 8080, 8081, 8082
        hosts:
            * "primary"
            * "backup"
            * "local"

        matrix:
            * 1, 22, 333
            * 4444, 5, 66
            * 77, 888, 9

        commented_values:
            * 1 # Keep this explanation with its value.
            * 2
    """.trimIndent() + "\n"
}
