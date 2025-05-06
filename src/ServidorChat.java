import java.io.*;
import java.net.*;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/*
clase Usuario

Crea un usuario conectado identificado con un ID, crea la conexión entre los sockets de texto/mensajes
y el socket de archivos. El constructor inicializa todos los atributos de la clase.
*/
class Usuario 
{
    private String nombre;
    private String idUsuario;
    private Socket socketTexto;
    private Socket socketArchivos;

    public Usuario(String nombre, Socket socketTexto, Socket socketArchivos) 
    {
        this.nombre = nombre;
        this.idUsuario = nombre + "#" + socketTexto.getPort();
        this.socketTexto = socketTexto;
        this.socketArchivos = socketArchivos;
    }

    // Getters
    public String getNombreIdentificable()  { return nombre + "#" + socketTexto.getPort(); }
    public String getNombre() { return nombre; }
    public Socket getSocketTexto() { return socketTexto; }
    public Socket getSocketArchivos() { return socketArchivos; }
}

/*
clase GestorUsuarios

    - Método: ListaUsuarios
Regresa la lista de usuarios coenctados en ese momento.

    - Método: agregarUsuario
Almacena a los clientes/usuarios de forma segura para hilos donde despliega un mensaje de que dicho cliente
ha sido registrado.

    - Método: eliminarUsusario
Caundo se desea eliminar un usuario se crea un objeto de tipo usaurio donde se elimina un usuario
con cierto nombre. Tras la eliminación se cuerran los sockets de texto y archivos correspondientes
mandando un mensaje de usuario eliminado.
*/
class GestorUsuarios 
{
    private static final ConcurrentHashMap<String, Usuario> usuarios = new ConcurrentHashMap<>();
    
    public static String ListaUsuarios()
    {
        return usuarios.values().stream().map(Usuario::getNombreIdentificable).collect(Collectors.joining(","));
    }

    public static void agregarUsuario(String nombre, Socket socketTexto, Socket socketArchivos, PrintWriter pwTexto) 
    {
        Usuario nuevoUsuario = new Usuario(nombre, socketTexto, socketArchivos);
        usuarios.put(nuevoUsuario.getNombreIdentificable(), nuevoUsuario);
        pwTexto.println("NOMBRE_UNICO:" + nuevoUsuario.getNombreIdentificable());
        pwTexto.flush();
        System.out.println("\nUsuario '" + nuevoUsuario.getNombreIdentificable() + "' registrado.");
    }

    public static void eliminarUsuario(String nombre) 
    {
        Usuario usuario = usuarios.remove(nombre);
        if (usuario != null) 
        {
            try 
            {
                usuario.getSocketTexto().close();
                usuario.getSocketArchivos().close();
            } 
            catch (IOException e) 
            {
                System.err.println("Error al cerrar sockets de " + nombre);
            }
            System.out.println("Usuario '" + nombre + "' eliminado.");
        }
    }

    //getter
    public static Usuario getUsuario(String nombre) 
    {
        return usuarios.get(nombre);
    }
}

class GestorSalas
{
    private static final ConcurrentHashMap<String, Set<String>> salas = new ConcurrentHashMap<>();
    
    public static boolean crearSala(String nombreSala, String usuarioCreador)
    {
        Set<String> sala = salas.get(nombreSala);
        if(sala == null)
        {
            sala = ConcurrentHashMap.newKeySet();
            sala.add(usuarioCreador);
            salas.put(nombreSala, sala);
            return true;
        }
        return false;
    }
    
    public static String ListaSalas()
    {
        return String.join(",", salas.keySet());
    }
    
    public static boolean unirseSala(String nombreSala, String usuario)
    {
        Set<String> sala = salas.get(nombreSala);
        if(sala != null)
            return sala.add(usuario);
        return false;
    }
}

/*
clase: ManejadorCliente

Crea un hilo para soportar múltiples usaurios. Priemro inicializa y asigna los sockets de texto y archivos.
En el método run del hilo se lee el nombre del cliente y verifica que el nombre coicida en ambos sockets, si
coinciden lso nombres se invoca al método agregarUsuario con sus respectivos argumentos e imprime desde
donde se encuentra conectado el cliente.
*/
class ManejadorCliente extends Thread
{
    private Socket socketTexto;
    private Socket socketArchivos;
    
    public ManejadorCliente(Socket socketTexto, Socket socketArchivos)
    {
        this.socketTexto = socketTexto;
        this.socketArchivos = socketArchivos;
    }
    
    public void run()
    {
        String nombreUsuario = null;
        
        try(
            BufferedReader brTexto = new BufferedReader(new InputStreamReader(socketTexto.getInputStream(), "ISO-8859-1"));
            PrintWriter pwTexto = new PrintWriter(new OutputStreamWriter(socketTexto.getOutputStream(), "ISO-8859-1"));
            DataInputStream disArchivo = new DataInputStream(socketArchivos.getInputStream()))
        {
            nombreUsuario = brTexto.readLine();
            String nombreArchivo = disArchivo.readUTF();
            
            // Verificar que coincidan
            if (nombreUsuario != null && nombreUsuario.equals(nombreArchivo)) 
            {
                GestorUsuarios.agregarUsuario(nombreUsuario, socketTexto, socketArchivos, pwTexto);
                    
                String ipTexto = socketTexto.getInetAddress().getHostAddress();
                int puertoTexto = socketTexto.getPort();
                String ipArchivos = socketArchivos.getInetAddress().getHostAddress();
                int puertoArchivos = socketArchivos.getPort();
                    
                System.out.println("'" + nombreUsuario + "' conectado desde:\n"
                    + "- Texto/Mensajes: " + ipTexto + ": " + puertoTexto + "\n"
                    + "- Archivos: " + ipArchivos + ": " + puertoArchivos);
            } 
            else 
            {
                System.err.println("Error: Nombres no coinciden. Cerrando conexiones...");
                socketTexto.close();
                socketArchivos.close();
            }

            String instruccion;
            while((instruccion = brTexto.readLine()) != null)
            {
                if(instruccion.equals("OBTENER_USUARIOS"))
                {
                    String lista = GestorUsuarios.ListaUsuarios();
                    pwTexto.println("Lista:" + lista);
                    pwTexto.flush();
                }
                else if(instruccion.startsWith("CREAR_SALA:"))
                {
                    String nombreSala = instruccion.substring(11);
                    boolean salaCreada = GestorSalas.crearSala(nombreSala, nombreUsuario+"#"+socketTexto.getPort());
                    if(salaCreada)
                    {
                        pwTexto.println("SALA CREADA: " + nombreSala);
                    }
                    else
                        pwTexto.println("ERROR: La sala ya existe");
                    pwTexto.flush();
                }
                else if(instruccion.equals("OBTENER_SALAS"))
                {
                    String listaSalas = GestorSalas.ListaSalas();
                    pwTexto.println("Salas:" + listaSalas);
                    pwTexto.flush();
                }
                else if(instruccion.startsWith("MENSAJE_PRIVADO:"))
                {
                    String[] partes = instruccion.substring(16).split(":", 2);
                    String destinatario = partes[0];
                    String contenido = partes[1];
                    
                    Usuario usuarioDestino = GestorUsuarios.getUsuario(destinatario);
                    if(usuarioDestino != null)
                    {
                        PrintWriter pwDest = new PrintWriter(new OutputStreamWriter(usuarioDestino.getSocketTexto().getOutputStream(), "ISO-8859-1"));
                        pwDest.println("PRIVADO_DE:" + nombreUsuario+"#"+socketTexto.getPort()+":" + contenido);
                        pwDest.flush();
                    }
                    else
                    {
                        pwTexto.println("ERROR: Usuario no encontrado");
                        pwTexto.flush();
                    }
                }
            }
        }
        catch(IOException e)
        {
            System.err.println("Cliente " + nombreUsuario + "#" + socketTexto.getPort() + " desconectado");
        }
        finally
        {
            if(nombreUsuario != null)
                GestorUsuarios.eliminarUsuario(nombreUsuario + "#" + socketTexto.getPort());
        }
    }
}

//Clase principal
public class ServidorChat 
{
    public static void main(String[] args) 
    {
        //Crea los sockets de texto y archivos en los puertos 1234 y 1235
        try(
            ServerSocket servidorTexto = new ServerSocket(1234);
            ServerSocket servidorArchivos = new ServerSocket(1235))
        {
            System.out.println("Servidor iniciado...\nEsperando clientes...");

            while(true) 
            {
                // Esperar conexiones en ambos puertos
                Socket socketTexto = servidorTexto.accept();
                Socket socketArchivos = servidorArchivos.accept();

                //Crea los hilos para cada cliente
                (new ManejadorCliente(socketTexto, socketArchivos)).start();
            }
        } 
        catch (IOException e) 
        {
            System.err.println("Error en el servidor: " + e.getMessage());
        }
    }
}