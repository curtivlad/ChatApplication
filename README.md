# 💬 ChatApp - Aplicație de Chat în Rețea Locală (LAN)

[![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.java.com/)
[![License](https://img.shields.io/badge/license-MIT-blue.svg?style=for-the-badge)](LICENSE)

O aplicație desktop de chat în timp real pentru rețele locale (LAN), construită în Java, care permite utilizatorilor să comunice rapid și eficient fără nevoia unui server central sau conexiune la internet.

## 📋 Cuprins

- [Descriere](#-descriere)
- [Caracteristici](#-caracteristici)
- [Tehnologii Utilizate](#️-tehnologii-utilizate)
- [Arhitectură](#-arhitectură)
- [Instalare](#-instalare)
- [Utilizare](#-utilizare)
- [Structura Proiectului](#-structura-proiectului)
- [Contribuții](#-contribuții)
- [Licență](#-licență)

## 📖 Descriere

**ChatApp** este o aplicație de mesagerie peer-to-peer (P2P) care funcționează exclusiv în rețele locale (LAN). Aplicația utilizează protocolul **UDP** pentru descoperirea automată a utilizatorilor din rețea și **TCP** pentru transmiterea mesajelor, asigurând o comunicare fiabilă și în timp real.

Ideal pentru:
- 🏢 Comunicare rapidă în birouri și spații de lucru
- 🎓 Colaborare în săli de clasă și laboratoare
- 🏠 Chat casnic fără internet
- 🔒 Comunicare privată în rețele izolate

## ✨ Caracteristici

### Funcționalități Principale

- **🔍 Auto-Discovery**: Detectare automată a utilizatorilor din rețea folosind UDP broadcast
- **💬 Chat în Timp Real**: Mesagerie instantanee prin conexiuni TCP
- **👥 Multi-utilizator**: Suport pentru conversații cu mai mulți utilizatori simultan
- **🎨 Interfață Grafică Intuitivă**: UI prietenos construit cu Java Swing
- **🔔 Notificări**: Alertare la mesaje noi și când utilizatori noi se conectează
- **📊 Status Utilizatori**: Vizualizare în timp real a utilizatorilor activi din rețea
- **🚀 Fără Server Central**: Arhitectură descentralizată, fiecare client funcționează independent
- **🔐 Comunicare Locală**: Toate datele rămân în rețeaua locală, fără acces extern

### Caracteristici Tehnice

- Protocol UDP pentru network discovery (port configurabil)
- Protocol TCP pentru transmiterea mesajelor (conexiuni persistente)
- Threading pentru gestionarea conexiunilor multiple
- Serializare obiectelor pentru transmiterea datelor
- Gestionarea erorilor de rețea și reconectare automată

## 🛠️ Tehnologii Utilizate

### Core Technologies

| Tehnologie | Scop |
|-----------|------|
| **Java SE** | Limbaj de programare principal |
| **Java Swing** | Framework pentru interfața grafică |
| **Java Networking (java.net)** | Comunicare în rețea (Socket, DatagramSocket) |
| **Java I/O (java.io)** | Operații input/output pentru streams |
| **Multithreading** | Gestionare concurentă a conexiunilor |

### Protocoale de Rețea

- **UDP (User Datagram Protocol)**: Broadcast pentru descoperirea utilizatorilor
- **TCP (Transmission Control Protocol)**: Transfer fiabil al mesajelor

## 🏗️ Arhitectură

### Componente Principale

```
┌─────────────────────────────────────────────────┐
│                  ChatApp Client                  │
├─────────────────────────────────────────────────┤
│                                                  │
│  ┌──────────────┐         ┌──────────────┐     │
│  │   GUI Layer  │◄───────►│ Main Window  │     │
│  └──────┬───────┘         └──────────────┘     │
│         │                                        │
│  ┌──────▼────────────────────────────────────┐  │
│  │        Application Layer                  │  │
│  │  ┌──────────┐        ┌──────────┐        │  │
│  │  │ Message  │        │   User   │        │  │
│  │  │ Handler  │        │ Manager  │        │  │
│  │  └──────────┘        └──────────┘        │  │
│  └───────────────────────────────────────────┘  │
│         │                       │                │
│  ┌──────▼───────────────────────▼──────────┐    │
│  │         Network Layer                   │    │
│  │  ┌────────────┐     ┌────────────┐     │    │
│  │  │    UDP     │     │    TCP     │     │    │
│  │  │  Discovery │     │  Messaging │     │    │
│  │  │  Listener  │     │   Socket   │     │    │
│  │  └────────────┘     └────────────┘     │    │
│  └─────────────────────────────────────────┘    │
└─────────────────────────────────────────────────┘
```

### Flux de Comunicare

#### 1. **Descoperirea Utilizatorilor (UDP)**

```
Client A                    LAN Network                    Client B
   │                                                           │
   │──────── UDP Broadcast "DISCOVER" ──────────────────────► │
   │                     (Port 8888)                           │
   │                                                           │
   │ ◄──────── UDP Response "HERE" ──────────────────────────│
   │              (User Info + IP)                             │
```

#### 2. **Transmiterea Mesajelor (TCP)**

```
Client A                                                   Client B
   │                                                           │
   │────────── TCP Connect (Port 8889) ────────────────────► │
   │                                                           │
   │────────── Send Message Object ─────────────────────────► │
   │            (Serialized)                                   │
   │                                                           │
   │ ◄────────── TCP ACK ───────────────────────────────────│
```

## 📦 Instalare

### Cerințe de Sistem

- **Java Runtime Environment (JRE)**: Versiunea 8 sau mai recentă
- **Java Development Kit (JDK)**: Versiunea 8 sau mai recentă (pentru compilare)
- **Sistem de operare**: Windows, macOS, Linux
- **Rețea**: Conexiune la o rețea locală (LAN)

### Pași de Instalare

#### Opțiunea 1: Clonare și Compilare

```bash
# 1. Clonează repository-ul
git clone https://github.com/curtivlad/ChatApplication.git

# 2. Navighează în directorul proiectului
cd ChatApplication/ChatApp

# 3. Compilează sursele
javac -d bin src/**/*.java

# 4. Rulează aplicația
java -cp bin Main
```

#### Opțiunea 2: Import în IDE

**Pentru IntelliJ IDEA:**

1. File → Open → Selectează folderul `ChatApp`
2. Așteaptă indexarea proiectului
3. Run → Run 'Main'

**Pentru Eclipse:**

1. File → Import → Existing Projects into Workspace
2. Selectează directorul `ChatApp`
3. Right-click pe proiect → Run As → Java Application

#### Opțiunea 3: Executabil JAR (dacă este disponibil)

```bash
java -jar ChatApp.jar
```

## 🚀 Utilizare

### Pornirea Aplicației

1. **Lansare**: Rulează aplicația pe fiecare calculator din rețeaua LAN
2. **Auto-detectare**: Aplicația va scana automat rețeaua și va afișa utilizatorii disponibili
3. **Pornește conversația**: Selectează un utilizator din listă și începe să trimiți mesaje

### Interfața Utilizator

```
┌──────────────────────────────────────────────────────────┐
│  ChatApp - [Numele Tău]                          [ _ □ X ]│
├──────────────────────────────────────────────────────────┤
│                                                           │
│  ┌─────────────────┐  ┌─────────────────────────────┐    │
│  │  Utilizatori    │  │    Conversație              │    │
│  │  Online         │  │                             │    │
│  │                 │  │  User1: Salut!              │    │
│  │  ● User1        │  │  Tu: Bună ziua!             │    │
│  │  ● User2        │  │  User1: Ce mai faci?        │    │
│  │  ● User3        │  │                             │    │
│  │                 │  │                             │    │
│  └─────────────────┘  └─────────────────────────────┘    │
│                                                           │
│  ┌─────────────────────────────────────────────────┐     │
│  │ Scrie mesajul tău aici...               [Trimite]│    │
│  └─────────────────────────────────────────────────┘     │
└──────────────────────────────────────────────────────────┘
```

### Comenzi și Controale

| Acțiune | Metodă |
|---------|--------|
| Trimite mesaj | Enter sau click pe butonul "Trimite" |
| Selectează utilizator | Click pe numele utilizatorului din listă |
| Închide aplicația | Click pe X sau File → Exit |

### Configurare Rețea

#### Setări Firewall

Pentru funcționare corectă, asigură-te că următoarele porturi sunt deschise în firewall:

- **Port UDP**: 8888 (pentru discovery)
- **Port TCP**: 8889 (pentru messaging)

**Windows Firewall:**

```powershell
# Permite trafic UDP pe portul 8888
netsh advfirewall firewall add rule name="ChatApp UDP" dir=in action=allow protocol=UDP localport=8888

# Permite trafic TCP pe portul 8889
netsh advfirewall firewall add rule name="ChatApp TCP" dir=in action=allow protocol=TCP localport=8889
```

**Linux (ufw):**

```bash
sudo ufw allow 8888/udp
sudo ufw allow 8889/tcp
```

## 📁 Structura Proiectului

```
ChatApp/
│
├── src/
│   ├── main/
│   │   └── Main.java                  # Punct de intrare al aplicației
│   │
│   ├── network/
│   │   ├── UDPDiscovery.java         # Descoperire utilizatori prin UDP
│   │   ├── TCPMessageSender.java     # Trimitere mesaje prin TCP
│   │   ├── TCPMessageReceiver.java   # Primire mesaje prin TCP
│   │   └── NetworkConfig.java        # Configurări de rețea
│   │
│   ├── models/
│   │   ├── User.java                 # Model pentru utilizator
│   │   ├── Message.java              # Model pentru mesaj
│   │   └── ChatSession.java          # Sesiune de chat
│   │
│   ├── ui/
│   │   ├── MainWindow.java           # Fereastra principală
│   │   ├── ChatPanel.java            # Panoul de conversație
│   │   ├── UserListPanel.java        # Lista utilizatori
│   │   └── MessagePanel.java         # Afișare mesaje
│   │
│   └── utils/
│       ├── MessageSerializer.java    # Serializare/deserializare mesaje
│       ├── NetworkUtils.java         # Utilități de rețea
│       └── Logger.java               # Logging sistem
│
├── resources/
│   ├── icons/                        # Iconițe UI
│   └── config.properties             # Fișier configurare
│
├── lib/                              # Biblioteci externe (dacă există)
│
├── README.md                         # Acest fișier
├── LICENSE                           # Licență
└── .gitignore                        # Git ignore rules
```

## 🔧 Configurare Avansată

### Modificarea Porturilor

Editează fișierul `config.properties`:

```properties
# Port pentru UDP Discovery
udp.port=8888

# Port pentru TCP Messaging
tcp.port=8889

# Timeout pentru conexiuni (ms)
connection.timeout=5000

# Interval de reîncercare pentru discovery (ms)
discovery.interval=10000
```

### Parametri în Cod

În clasa `NetworkConfig.java`:

```java
public class NetworkConfig {
    public static final int UDP_PORT = 8888;
    public static final int TCP_PORT = 8889;
    public static final int BUFFER_SIZE = 8192;
    public static final int DISCOVERY_INTERVAL = 10000; // 10 secunde
}
```

## 🤝 Contribuții

Contribuțiile sunt binevenite! Dacă dorești să îmbunătățești acest proiect:

1. **Fork** repository-ul
2. **Creează** un branch pentru feature-ul tău (`git checkout -b feature/AmazingFeature`)
3. **Commit** modificările (`git commit -m 'Add some AmazingFeature'`)
4. **Push** la branch (`git push origin feature/AmazingFeature`)
5. **Deschide** un Pull Request

### Idei pentru Contribuții

- 🔐 Implementare criptare end-to-end
- 📁 Suport pentru transferul de fișiere
- 🎨 Teme personalizabile pentru UI
- 📝 Istoric conversații persistente
- 🔔 Sunet pentru notificări
- 🌍 Internationalizare (i18n)
- 👥 Chat de grup (multiple persoane simultan)
- 📱 Versiune Android/iOS pentru rețele locale

## 🐛 Raportare Bug-uri

Dacă întâmpini probleme:

1. Verifică Issues existente pentru a vedea dacă problema a fost raportată
2. Dacă nu, creează un Issue nou cu:
   - Descrierea detaliată a problemei
   - Pași pentru reproducere
   - Sistem de operare și versiune Java
   - Screenshot-uri (dacă sunt relevante)

## 📄 Licență

Acest proiect este licențiat sub [MIT License](LICENSE) - vezi fișierul LICENSE pentru detalii.

## 👨‍💻 Autor

**Vlad Curti**

- GitHub: [@curtivlad](https://github.com/curtivlad)
- LinkedIn: [Vlad Curti](https://linkedin.com/in/vlad-curti)

## 🙏 Mulțumiri

- Comunitatea Java pentru documentație excelentă
- Contribuitori open-source pentru inspirație și exemple
- Toate persoanele care testează și oferă feedback

## 📚 Resurse Suplimentare

### Documentație Java Networking

- [Java Networking Tutorial](https://docs.oracle.com/javase/tutorial/networking/)
- [Socket Programming în Java](https://docs.oracle.com/javase/8/docs/api/java/net/Socket.html)
- [DatagramSocket Documentation](https://docs.oracle.com/javase/8/docs/api/java/net/DatagramSocket.html)

### Tutoriale

- [Building a Chat Application in Java](https://www.baeldung.com/java-client-server-application)
- [UDP vs TCP: When to Use What](https://www.cloudflare.com/learning/ddos/glossary/tcp-ip/)

---

⭐ Dacă îți place acest proiect, oferă-i un star pe GitHub! ⭐

**Realizat cu ❤️ și ☕ de Vlad Curti**
