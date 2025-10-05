import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Scanner;

public class Cliente {

    private static final String SERVER_ADDRESS = "127.0.0.1";
    private static final int SERVER_PORT = 8080;

    // Códigos de Operação do Cliente
    private static final byte OP_INSERIR = 1;
    private static final byte OP_LISTAR = 2;

    // Códigos de Resposta do Servidor
    private static final byte OP_RESPONSE = 3;
    private static final byte STATUS_SUCCESS = 0;
    private static final byte STATUS_ERROR_DUPLICATE_CPF = 1;
    private static final byte STATUS_LIST_ITEM = 2;
    private static final byte STATUS_END_OF_LIST = 3;

    public static void main(String[] args) {
        Scanner consoleScanner = new Scanner(System.in);
        while (true) {
            System.out.println("\n===== MENU (PROTOCOLO BINARIO) =====");
            System.out.println("1 - Inserir Pessoa");
            System.out.println("2 - Listar Pessoas");
            System.out.println("3 - Sair");
            System.out.print("Escolha uma opcao: ");
            String choice = consoleScanner.nextLine();
            switch (choice) {
                case "1": handleInserir(consoleScanner); break;
                case "2": handleListar(); break;
                case "3":
                    System.out.println("Saindo...");
                    consoleScanner.close();
                    return;
                default: System.out.println("Opcao invalida.");
            }
        }
    }

    private static void handleInserir(Scanner scanner) {
        // --- Leitura e Validação dos dados ---
        System.out.print("Digite o CPF (pode usar . e -): ");
        String cpf = scanner.nextLine().replaceAll("[.\\-]", "");
        if (!cpf.matches("\\d{11}")) { System.err.println("Erro: CPF invalido. Deve conter 11 digitos."); return; }

        System.out.print("Digite o Nome completo: ");
        String nome = scanner.nextLine();
        byte[] nomeBytes = nome.getBytes(StandardCharsets.UTF_8);
        if (nomeBytes.length > 255) { System.err.println("Erro: Nome muito longo (max 255 bytes)."); return; }

        System.out.print("Digite a Data de Nascimento (DD/MM/AAAA ou DDMMAAAA): ");
        String dataNascInput = scanner.nextLine().replaceAll("[/]", "");
        String dataNascFormatada;
        try {
            if(dataNascInput.length() != 8) throw new DateTimeParseException("Tamanho inválido", dataNascInput, 0);
            LocalDate data = LocalDate.parse(dataNascInput, DateTimeFormatter.ofPattern("ddMMuuuu"));
            dataNascFormatada = data.format(DateTimeFormatter.ofPattern("dd/MM/uuuu"));
        } catch (DateTimeParseException e) { System.err.println("Erro: Data de nascimento invalida! Use o formato DDMMAAAA."); return; }

        System.out.println("Digite o Telefone (11 digitos, DDD + Numero. Ex: 62987654321):");
        String telefone = scanner.nextLine().trim();
        if (!telefone.matches("\\d{11}")) { System.err.println("Erro: O telefone deve conter exatamente 11 digitos."); return; }

        try (
                Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                DataInputStream dis = new DataInputStream(socket.getInputStream())
        ) {
            // Cria o payload em um buffer de bytes separado para calcular o tamanho
            ByteArrayOutputStream payloadStream = new ByteArrayOutputStream();
            payloadStream.write(cpf.getBytes(StandardCharsets.UTF_8));
            payloadStream.write((byte)nomeBytes.length);
            payloadStream.write(nomeBytes);
            payloadStream.write(dataNascFormatada.getBytes(StandardCharsets.UTF_8));
            payloadStream.write(telefone.getBytes(StandardCharsets.UTF_8));

            byte[] payload = payloadStream.toByteArray();

            // Envia o pacote completo (Cabeçalho + Payload)
            dos.writeByte(OP_INSERIR);
            dos.writeInt(payload.length); // DataOutputStream já escreve em Big-endian
            dos.write(payload);
            dos.flush();

            System.out.println("\nDados binarios enviados. Aguardando resposta...");

            // Leitura da resposta
            byte opCode = dis.readByte();
            dis.readInt(); // Lê e descarta o tamanho do payload
            byte status = dis.readByte();

            if (opCode == OP_RESPONSE) {
                if (status == STATUS_SUCCESS) {
                    System.out.println("Resposta do servidor: SUCESSO - Pessoa inserida.");
                } else if (status == STATUS_ERROR_DUPLICATE_CPF) {
                    System.out.println("Resposta do servidor: ERRO - CPF ja cadastrado.");
                } else {
                    System.out.println("Resposta do servidor: Status desconhecido " + status);
                }
            }

        } catch (Exception e) {
            System.err.println("Erro de comunicacao com o servidor: " + e.getMessage());
        }
    }

    private static void handleListar() {
        try (
                Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                DataInputStream dis = new DataInputStream(socket.getInputStream())
        ) {
            // Envia requisição de LISTAR (sem payload)
            dos.writeByte(OP_LISTAR);
            dos.writeInt(0);
            dos.flush();

            System.out.println("\n--- Lista de Pessoas Cadastradas ---");

            while (true) {
                byte opCode = dis.readByte();
                int payloadLen = dis.readInt();

                if (opCode != OP_RESPONSE) continue; // Ignora pacotes inesperados

                byte status = dis.readByte();

                if (status == STATUS_END_OF_LIST) {
                    break; // Fim da lista
                }

                if (status == STATUS_LIST_ITEM) {
                    // O resto do payload (payloadLen - 1) são os dados da pessoa
                    byte[] personData = new byte[payloadLen - 1];
                    dis.readFully(personData);

                    // Parse do payload da pessoa
                    String cpf = new String(personData, 0, 11, StandardCharsets.UTF_8);
                    int nomeLen = personData[11] & 0xFF; // Converte byte sem sinal para int
                    String nome = new String(personData, 12, nomeLen, StandardCharsets.UTF_8);
                    int nomeEndIndex = 12 + nomeLen;
                    String dataNasc = new String(personData, nomeEndIndex, 10, StandardCharsets.UTF_8);
                    String telefone = new String(personData, nomeEndIndex + 10, 11, StandardCharsets.UTF_8);

                    System.out.printf("CPF: %-15s | Nome: %-25s | Nasc: %-12s | Tel: %s\n",
                            cpf, nome, dataNasc, telefone);
                }
            }
            System.out.println("------------------------------------");

        } catch (EOFException e) {
            System.out.println("Fim da transmissao de dados.");
        } catch (Exception e) {
            System.err.println("Erro de comunicacao com o servidor: " + e.getMessage());
        }
    }
}