#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <stdint.h> // Para uint32_t, uint8_t

#define PORT 8080
#define DB_FILE "pessoas.bin"

// Para garantir alinhamento de memória correto entre C e rede
#pragma pack(push, 1)
typedef struct {
    char cpf[11];
    uint8_t nome_len;
    // O nome em si virá depois, pois é de tamanho variável
    char data_nasc[10];
    char telefone[11];
} PersonPayload;
#pragma pack(pop)

typedef enum {
    STATUS_SUCCESS = 0,
    STATUS_ERROR_DUPLICATE_CPF = 1,
    STATUS_LIST_ITEM = 2,
    STATUS_END_OF_LIST = 3
} ServerStatus;

// Função auxiliar para enviar uma resposta simples (sem payload de dados)
void send_simple_response(int socket, ServerStatus status) {
    uint8_t op_code = 3; // Resposta Servidor
    uint32_t payload_len_n = htonl(1); // Payload é apenas 1 byte (o status)
    uint8_t status_byte = status;

    send(socket, &op_code, 1, 0);
    send(socket, &payload_len_n, 4, 0);
    send(socket, &status_byte, 1, 0);
}

int cpf_exists(const char* cpf) {
    FILE* db = fopen(DB_FILE, "rb");
    if (!db) return 0;

    char file_cpf[11];
    uint8_t nome_len;

    // Lê o campo CPF (11 bytes)
    while (fread(file_cpf, 11, 1, db) == 1) {
        if (strncmp(cpf, file_cpf, 11) == 0) {
            fclose(db);
            return 1;
        }
        // Pula o resto do registro para ir para o próximo CPF
        fread(&nome_len, 1, 1, db); // lê o tamanho do nome
        fseek(db, nome_len + 10 + 11, SEEK_CUR); // pula nome + data + telefone
    }
    fclose(db);
    return 0;
}

void handle_connection(int client_socket) {
    uint8_t op_code;
    if (read(client_socket, &op_code, 1) <= 0) {
        close(client_socket);
        return;
    }

    uint32_t payload_len_n; // n = network byte order
    if (read(client_socket, &payload_len_n, 4) <= 0) {
        close(client_socket);
        return;
    }
    uint32_t payload_len = ntohl(payload_len_n);

    char* buffer = malloc(payload_len);
    if(buffer == NULL) {
        perror("Falha ao alocar buffer");
        close(client_socket);
        return;
    }
    read(client_socket, buffer, payload_len);

    switch (op_code) {
        case 1: { // INSERIR
            // Pega o CPF de dentro do buffer
            char received_cpf[12];
            strncpy(received_cpf, buffer, 11);
            received_cpf[11] = '\0';

            if (cpf_exists(received_cpf)) {
                printf("Tentativa de inserir CPF duplicado: %s\n", received_cpf);
                send_simple_response(client_socket, STATUS_ERROR_DUPLICATE_CPF);
            } else {
                FILE* db = fopen(DB_FILE, "ab");
                if (db) {
                    fwrite(buffer, 1, payload_len, db);
                    fclose(db);
                    printf("Pessoa inserida: %s\n", received_cpf);
                    send_simple_response(client_socket, STATUS_SUCCESS);
                } else {
                    perror("Nao foi possivel abrir o banco de dados");
                }
            }
            break;
        }
        case 2: { // LISTAR
            FILE* db = fopen(DB_FILE, "rb");
            if (db) {
                // Buffer grande para conter o maior registro possível
                char record_buffer[sizeof(PersonPayload) + 255];

                while(1){
                    // Lê os campos de tamanho fixo primeiro para obter o nome_len
                    PersonPayload header;
                    if(fread(header.cpf, 11, 1, db) != 1) break;
                    if(fread(&header.nome_len, 1, 1, db) != 1) break;

                    uint32_t person_payload_len = sizeof(PersonPayload) + header.nome_len;

                    // Monta o payload completo
                    memcpy(record_buffer, header.cpf, 11);
                    memcpy(record_buffer + 11, &header.nome_len, 1);

                    // Lê o resto dos dados (nome + data + telefone)
                    size_t remaining_len = header.nome_len + 10 + 11;
                    if(fread(record_buffer + 12, remaining_len, 1, db) != 1) break;

                    // Envia o pacote de resposta
                    uint8_t resp_op_code = 3;
                    uint32_t resp_payload_len = 1 + person_payload_len; // status + dados pessoa
                    uint32_t resp_payload_len_n = htonl(resp_payload_len);
                    uint8_t status = STATUS_LIST_ITEM;

                    send(client_socket, &resp_op_code, 1, 0);
                    send(client_socket, &resp_payload_len_n, 4, 0);
                    send(client_socket, &status, 1, 0);
                    send(client_socket, record_buffer, person_payload_len, 0);
                }
                fclose(db);
            }
            send_simple_response(client_socket, STATUS_END_OF_LIST);
            printf("Lista de pessoas enviada.\n");
            break;
        }
    }
    free(buffer);
    close(client_socket);
}

int main() {
    int server_socket, client_socket;
    struct sockaddr_in server_addr, client_addr;
    socklen_t client_addr_size;

    // Criar o socket do servidor
    server_socket = socket(AF_INET, SOCK_STREAM, 0);
    if (server_socket == -1) {
        perror("Nao foi possivel criar o socket");
        return 1;
    }

    // Configurar o endereço do servidor
    server_addr.sin_family = AF_INET;
    server_addr.sin_addr.s_addr = INADDR_ANY; // Aceita conexões de qualquer IP
    server_addr.sin_port = htons(PORT);

    // Associar (bind) o socket ao endereço e porta
    if (bind(server_socket, (struct sockaddr *)&server_addr, sizeof(server_addr)) < 0) {
        perror("Bind falhou");
        close(server_socket);
        return 1;
    }

    // Escutar por conexões
    listen(server_socket, 5);

    printf("Servidor Binario (v3) escutando na porta %d...\n", PORT);

    while (1) {
        client_addr_size = sizeof(client_addr);
        client_socket = accept(server_socket, (struct sockaddr *)&client_addr, &client_addr_size);

        if (client_socket < 0) {
            perror("Accept falhou");
            continue;
        }

        printf("Conexao aceita.\n");
        handle_connection(client_socket);
    }

    close(server_socket);
    return 0;
}