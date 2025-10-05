# Cliente-Servidor C/Java com Protocolo Binário Customizado


Este repositório contém um projeto acadêmico que implementa uma aplicação cliente/servidor para cadastro de pessoas.

O projeto evoluiu de uma implementação simples com protocolo textual para uma solução com serialização binária.

## Principais Conceitos

  * **Arquitetura Cliente/Servidor:** Design e implementação da comunicação entre dois processos distintos.
  * **Programação de Sockets:** Uso das APIs de socket em C (`sys/socket.h`) e Java (`java.net.Socket`).
  * **Design de Protocolo de Camada de Aplicação:** Criação de um conjunto de regras (protocolo) para a troca de mensagens estruturadas.
  * **Serialização/Desserialização Binária:** Conversão de estruturas de dados em um fluxo de bytes e vice-versa, sem o *overhead* de formatos textuais.
  * **Tratamento de Endianness:** Uso de *Network Byte Order* (Big-Endian) para garantir a comunicação correta entre diferentes arquiteturas de máquina.
  * **Persistência de Dados:** Armazenamento dos registros em um arquivo binário no lado do servidor.
  * **Validação de Dados:** Regras de negócio implementadas tanto no cliente (formato de entrada) quanto no servidor (unicidade de dados).

## Funcionalidades

  * **Inserir Pessoa:** Cadastra uma nova pessoa com CPF, Nome, Data de Nascimento e Telefone.
  * **Listar Pessoas:** Exibe todos os registros armazenados no servidor.
  * **Validação de CPF Único:** O servidor rejeita o cadastro de um CPF que já exista na base.
  * **Validação de Dados no Cliente:** O cliente valida o formato do CPF, a validade da data de nascimento e o formato do telefone antes do envio.
  * **Persistência Binária:** Os dados são salvos no arquivo `pessoas.bin` no servidor, garantindo que não sejam perdidos ao reiniciar.

## Arquitetura do Protocolo Binário

A comunicação é baseada na troca de Unidades de Dados do Protocolo (PDU), ou datagramas, com uma estrutura fixa de **Cabeçalho + Carga Útil (Payload)**.

#### Estrutura do Datagrama

| Campo | Tamanho (bytes) | Descrição |
| :--- | :--- | :--- |
| **Código da Operação** | 1 | Define a ação (`INSERIR`, `LISTAR`, `RESPOSTA`). |
| **Tamanho do Payload** | 4 | Inteiro em *Network Byte Order* que especifica o tamanho exato da carga útil. |
| **Payload** | N (variável) | Os dados da operação, serializados em formato binário. |

-----

#### Códigos de Operação (`OpCode`)

| Código | Operação | Origem | Descrição |
| :--- | :--- | :--- | :--- |
| `1` | `INSERIR` | Cliente | Envia os dados de uma nova pessoa para cadastro. |
| `2` | `LISTAR` | Cliente | Solicita a lista completa de pessoas. |
| `3` | `RESPOSTA` | Servidor | Envia uma resposta para uma solicitação do cliente. |

-----

#### Payload de `RESPOSTA` (Status)

O primeiro byte do payload de uma resposta do servidor indica o status da operação.

| Código de Status | Status | Descrição |
| :--- | :--- | :--- |
| `0` | `SUCCESS` | Operação de inserção bem-sucedida. |
| `1` | `ERROR_DUPLICATE_CPF` | Erro: o CPF enviado já está cadastrado. |
| `2` | `LIST_ITEM` | A carga útil contém os dados de uma pessoa da lista. |
| `3` | `END_OF_LIST` | Sinaliza o fim da transmissão da lista de pessoas. |

## Tecnologias Utilizadas

  * **Servidor:**
      * Linguagem: **C**
      * Bibliotecas: `sys/socket.h`, `netinet/in.h`, `arpa/inet.h`, `stdint.h`, `stdio.h`, `stdlib.h`
  * **Cliente:**
      * Linguagem: **Java**
      * Classes Principais: `java.net.Socket`, `DataInputStream`, `DataOutputStream`
  * **Ambiente de Compilação:**
      * **GCC** (GNU Compiler Collection) para C
      * **JDK** (Java Development Kit) 8 ou superior

## Como Executar o Projeto

### Pré-requisitos

Certifique-se de ter o GCC e o JDK instalados em sua máquina.

```bash
# Para sistemas baseados em Debian/Ubuntu
sudo apt-get update
sudo apt-get install build-essential default-jdk
```

### Passos

1.  **Clone o repositório:**

    ```bash
    git clone <URL_DO_SEU_REPOSITORIO>
    cd <NOME_DA_PASTA>
    ```

2.  **Compile e execute o Servidor (C):**

      * Abra um terminal na pasta do projeto.
      * Compile o código do servidor:
        ```bash
        gcc servidor.c -o servidor
        ```
      * Execute o servidor:
        ```bash
        ./servidor
        ```
      * O terminal exibirá a mensagem `Servidor Binario (v3) escutando na porta 8080...` e ficará aguardando conexões. **Deixe este terminal aberto.**

3.  **Compile e execute o Cliente (Java):**

      * **Abra um novo terminal** na mesma pasta do projeto.
      * Compile o código do cliente:
        ```bash
        javac Cliente.java
        ```
      * Execute o cliente:
        ```bash
        java Cliente
        ```
      * O menu interativo da aplicação será exibido, e você poderá escolher as opções para inserir ou listar pessoas.

## Estrutura do Projeto

```
.
├── servidor.c      # Código fonte do servidor em C
├── Cliente.java    # Código fonte do cliente em Java
└── README.md       # Este arquivo
```
