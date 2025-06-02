import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/*
class Usuario

Define al usuario como un objeto, cada usaurio tiene los campos de nombre, nombre único, socket de texto para
mensajes y socket de archivos para envio de datos. Se define un cosntructor inicializando todos los datos.
Los getter obtiene los datos del usuario.
*/
class Usuario 
{
    private String nombre;
    private String idUsuario;
    private Socket socketTexto;
    private Socket socketArchivos;

    //Constructor de la clase Usuario
    public Usuario(String nombre, Socket socketTexto, Socket socketArchivos) 
    {
        this.nombre = nombre;
        this.idUsuario = nombre + "#" + socketTexto.getPort();  //Para identificar a cada usuario se le coloca el número de puerto
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
class GestorUsuario

La clase se encarga de gestionar los usuarios que se van registrando en el servidor, para guardar sus
datos y poder llevar a cabo diferentes tareas según la acción realizadas.
*/
class GestorUsuarios 
{
    //Almacena los usuarios activos en el servidor
    private static final ConcurrentHashMap<String, Usuario> usuarios = new ConcurrentHashMap<>();
    
    //Obtiene la lista de usaurios activos en el servidor (nombre identificable)
    public static String ListaUsuarios()
    {
        return usuarios.values().stream().map(Usuario::getNombreIdentificable).collect(Collectors.joining(","));
    }

    //Registra un nuevo usuario devolviendo al cliente el nombre único que le fue asignado
    public static void agregarUsuario(String nombre, Socket socketTexto, Socket socketArchivos, PrintWriter pwTexto) 
    {
        Usuario nuevoUsuario = new Usuario(nombre, socketTexto, socketArchivos);
        usuarios.put(nuevoUsuario.getNombreIdentificable(), nuevoUsuario);
        pwTexto.println("NOMBRE_UNICO:" + nuevoUsuario.getNombreIdentificable());
        pwTexto.flush();
        System.out.println("\nUsuario '" + nuevoUsuario.getNombreIdentificable() + "' registrado.");
    }

    //Elimina un usuario cuando se desconecta
    public static void eliminarUsuario(String nombre) 
    {
        //Remueve el usuario
        Usuario usuario = usuarios.remove(nombre);
        if (usuario != null)    
        {
            try 
            {
                //Cierra los sockets del usuario
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

    //getters
    public static Usuario getUsuario(String nombre) {   return usuarios.get(nombre);    }
    public static Socket getSocketArchivos(String nombre)
    {
        Usuario usuario = usuarios.get(nombre);
        return (usuario != null) ? usuario.getSocketArchivos() : null;
    }
}

/*
class GestorSalas

Gestiona las salas creadas durante la ejecución del servidor, guarda los datos de las salas
para poder llevar a cabo diferentes tareas según la acción realizada.
*/
class GestorSalas
{
    //Almacena el nombre de la sala y el usaurio
    public static final ConcurrentHashMap<String, Set<String>> salas = new ConcurrentHashMap<>();
    //Almacena el nombre de la sala y el usaurio creador
    public static final ConcurrentHashMap<String, String> creadores = new ConcurrentHashMap<>();
    
    //Crea una sala
    public static boolean crearSala(String nombreSala, String usuarioCreador)
    {
        //Obtiene los usuarios de la sala
        Set<String> sala = salas.get(nombreSala);
        if(sala == null)
        {
            //Añade la información al registro de las salas
            sala = ConcurrentHashMap.newKeySet();
            sala.add(usuarioCreador);
            salas.put(nombreSala, sala);
            creadores.put(nombreSala, usuarioCreador);
            System.out.println("Sala " + nombreSala + " creada por " + usuarioCreador);
            return true;
        }
        return false;
    }
    
    //Obtiene el listado de las salas existentes
    public static String ListaSalas()   {   return String.join(",", salas.keySet());    }
    
    //Añade un usuario nuevo a una sala
    public static boolean unirseSala(String nombreSala, String usuario)
    {
        //Verifica que exista la sala y obtiene los usaurios de la sala
        Set<String> sala = salas.get(nombreSala);
        if(sala != null)
        {
            //Verifica que el usuario no este en la sala
            boolean resultado = sala.add(usuario);
            if(resultado)
                System.out.println("Usuario " + usuario + " se unio a " + nombreSala);
            return resultado;
        }
        return false;
    }
    
    //Elimina un usaurio cunado se desconecta
    public static void eliminarUsuario(String usuario)
    {
        //Remueve al usuario de todas las salas a las que pertenezca
        for(Map.Entry<String, Set<String>> entry : salas.entrySet())
        {
            String nombreSala = entry.getKey();
            Set<String> sala = entry.getValue();
            
            if(sala.remove(usuario))
                System.out.println("Usuario " + usuario + " eliminado de la sala " + nombreSala);
        }
    }
    
    //Obtiene la lista de usuarios de una sala
    public static String listaUsuariosSala(String nombreSala)
    {
        Set<String> sala = salas.get(nombreSala);
        return (sala != null) ? String.join(", ", sala) : "La sala no existe";
    }
    
    //getter
    public static String obtenerCreador(String nombreSala)  {   return creadores.getOrDefault(nombreSala, "Desconocido");   }
}

/*
class ManejadorCliente

Crea un hilo para soportar múltiples usaurios. Priemro inicializa y asigna los sockets de texto y archivos.
En el método run del hilo se lee el nombre del cliente y verifica que el nombre coicida en ambos sockets, si
coinciden lso nombres se invoca al método agregarUsuario con sus respectivos argumentos e imprime desde
donde se encuentra conectado el cliente.
*/
class ManejadorCliente extends Thread
{
    private Socket socketTexto;
    private Socket socketArchivos;
    
    //Constructor
    public ManejadorCliente(Socket socketTexto, Socket socketArchivos)
    {
        this.socketTexto = socketTexto;
        this.socketArchivos = socketArchivos;
    }
    
    //Cuerpo del hilo
    public void run()
    {
        String nombreUsuario = null;
        
        //Crea br, pw, dis, y dos del usuario
        try(
            BufferedReader brTexto = new BufferedReader(new InputStreamReader(socketTexto.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter pwTexto = new PrintWriter(new OutputStreamWriter(socketTexto.getOutputStream(), StandardCharsets.UTF_8));
            DataInputStream disArchivo = new DataInputStream(socketArchivos.getInputStream());
            DataOutputStream dosArchivos = new DataOutputStream(socketArchivos.getOutputStream()))
        {
            //Obtiene el nombre del usuario por ambos sockets
            nombreUsuario = brTexto.readLine();
            String nombreArchivo = disArchivo.readUTF();
            
            // Verificar que coincidan ambos nombres
            if (nombreUsuario != null && nombreUsuario.equals(nombreArchivo)) 
            {
                //Agrega un usuario
                GestorUsuarios.agregarUsuario(nombreUsuario, socketTexto, socketArchivos, pwTexto);
                    
                String ipTexto = socketTexto.getInetAddress().getHostAddress();
                int puertoTexto = socketTexto.getPort();
                String ipArchivos = socketArchivos.getInetAddress().getHostAddress();
                int puertoArchivos = socketArchivos.getPort();
                    
                System.out.println("'" + nombreUsuario + "' conectado desde:\n"
                    + "- Texto/Mensajes: " + ipTexto + ": " + puertoTexto + "\n"
                    + "- Archivos: " + ipArchivos + ": " + puertoArchivos + "\n");
            } 
            else 
            {
                System.err.println("ERROR: Nombres no coinciden. Cerrando conexiones...");
                socketTexto.close();
                socketArchivos.close();
            }

            //Obtiene la instrucción desde el cliente
            String instruccion;
            while((instruccion = brTexto.readLine()) != null)
            {
                //Obtiene la lista de usaurios
                if(instruccion.equals("OBTENER_USUARIOS"))
                {
                    String lista = GestorUsuarios.ListaUsuarios();
                    pwTexto.println("Lista:" + lista);
                    pwTexto.flush();
                }
                //Crea una sala
                else if(instruccion.startsWith("CREAR_SALA:"))
                {
                    String nombreSala = instruccion.substring(11);
                    boolean salaCreada = GestorSalas.crearSala(nombreSala, nombreUsuario+"#"+socketTexto.getPort());
                    if(salaCreada)
                        pwTexto.println("SALA_CREADA:" + nombreSala);
                    else
                        pwTexto.println("ERROR: La sala ya existe");
                    pwTexto.flush();
                }
                //Obtiene la lista de salas
                else if(instruccion.equals("OBTENER_SALAS"))
                {
                    String listaSalas = GestorSalas.ListaSalas();
                    pwTexto.println("Salas:" + listaSalas);
                    pwTexto.flush();
                }
                //Reenvia el mensaje al cliente (de forma privada)
                else if(instruccion.startsWith("MENSAJE_PRIVADO:"))
                {
                    //Obtiene las partes del mensaje
                    String[] partes = instruccion.substring(16).split(":", 2);
                    String destinatario = partes[0];
                    String contenido = partes[1];
                    
                    //Obtiene al usuario destino y verifica si existe
                    Usuario usuarioDestino = GestorUsuarios.getUsuario(destinatario);
                    if(usuarioDestino != null)
                    {
                        //Crea un pw para mandar el mensaje al cliente y envia el mensaje
                        PrintWriter pwDest = new PrintWriter(new OutputStreamWriter(usuarioDestino.getSocketTexto().getOutputStream(), StandardCharsets.UTF_8));
                        pwDest.println("PRIVADO_DE:" + nombreUsuario+"#"+socketTexto.getPort()+":" + contenido);
                        pwDest.flush();
                    }
                    else
                    {
                        //Devuelve un error si no encuentra el usuario
                        pwTexto.println("ERROR: Usuario no encontrado");
                        pwTexto.flush();
                    }
                }
                //Obtiene la información de la sala
                else if(instruccion.startsWith("OBTENER_INFO_SALA:"))
                {
                    String nombreSala = instruccion.substring(18);
                    Set<String> sala = GestorSalas.salas.get(nombreSala);
                    
                    if(sala != null)
                    {
                        //Obtiene el creador y los usaurios de la sala
                        String creador = GestorSalas.obtenerCreador(nombreSala);
                        String usuarios = GestorSalas.listaUsuariosSala(nombreSala);
                        
                        pwTexto.println("INFO_SALA:" + nombreSala + ":" + creador + ":" + usuarios);
                    }
                    else
                        pwTexto.println("ERROR: La sala " + nombreSala + " no existe");
                    pwTexto.flush();
                }
                //Une a un usaurio a una sala
                else if(instruccion.startsWith("UNIRSE_A_SALA:"))
                {
                    //Obtiene el nombre único de un usuario
                    String nombreSala = instruccion.substring(14);
                    String usuario = nombreUsuario + "#" + socketTexto.getPort();
                    
                    //Añade al usuario a la sala
                    if(GestorSalas.unirseSala(nombreSala, usuario))
                    {
                        pwTexto.println("UNIDO_A_SALA:" + nombreSala);
                        pwTexto.flush();
                        Set<String> sala = GestorSalas.salas.get(nombreSala);
                        String creador = GestorSalas.obtenerCreador(nombreSala);
                        String usuarios = String.join(", ", sala);
                        //Devuelve la información actualizada de la sala
                        pwTexto.println("INFO_SALA:" + nombreSala + ":" + creador + ":" + usuarios);
                        pwTexto.flush();
                    }
                }
                //Reenvia un mensaje a la sala
                else if(instruccion.startsWith("MENSAJE_PUBLICO:"))
                {
                    //Obtiene las partes de la instrucción
                    String[] partes = instruccion.substring(16).split(":", 2);
                    String destinatario = partes[0];
                    String mensaje = partes[1];
                    
                    //Obtiene la sala a la que se quiere mandar
                    Set<String> sala = GestorSalas.salas.get(destinatario);
                    //Verifica que la sala exista
                    if(sala != null)
                    {
                        //Obtiene el nombre del usuario que manda el mensaje
                        String remitente = nombreUsuario + "#" + socketTexto.getPort();
                        //Verifica que el remitente sea usuario de la sala
                        if(sala.contains(remitente))
                        {
                            //Itera sobre todos los usaurios de la sala
                            for(String miembro : sala)
                            {
                                //No envia el mensaje al remitente
                                if(!miembro.equals(remitente))
                                {
                                    try
                                    {
                                        //Manda el mensaje de froma similar que el mensaje privado
                                        Usuario usuarioDestino = GestorUsuarios.getUsuario(miembro);
                                        if(usuarioDestino != null)
                                        {
                                            PrintWriter pwDest = new PrintWriter(new OutputStreamWriter(usuarioDestino.getSocketTexto().getOutputStream(), StandardCharsets.UTF_8));
                                            pwDest.println("PUBLICO_EN_SALA:" + destinatario + ":" + remitente + ":" + mensaje);
                                            pwDest.flush();
                                        }
                                    }
                                    catch(Exception e)
                                    {
                                        //Manda error si no se ecuentra un cierto miembro de la sala
                                        pwTexto.println("ERROR: Usuario '" + miembro + "' no encontrado");
                                        pwTexto.flush();
                                    }
                                }
                            }
                        }
                        else
                        {
                            //Devuelve error si el usuario no pertenece a la sala
                            pwTexto.println("ERROR: No eres miembro de la sala '" + destinatario + "'");
                            pwTexto.flush();
                        }
                    }
                    else
                    {
                        //Devuelve error si la sala no existe
                        pwTexto.println("ERROR: La sala '" + destinatario + "' no existe");
                        pwTexto.flush();
                    }
                }
                //Envia un archivo
                else if(instruccion.startsWith("ENVIAR_ARCHIVOS:"))
                {
                    //Obtiene las variables de nombre e instrucción para poder usarlas en el hilo
                    final String nomUsuario = nombreUsuario;
                    final String instruccionArchivos = instruccion;
                    new Thread(() ->
                    {
                        try
                        {
                            //Obtiene las partes de la isntrucción
                            String[] partes = instruccionArchivos.substring(16).split(":", 2);
                            String tipo = partes[0];
                            String destino = partes[1];

                            //Lee el número de archivos a recibir y reenviar
                            int numArchivos = disArchivo.readInt();
                            
                            //Verifica si el mensaje es privado o público (sala)
                            if(tipo.equals("USUARIO"))
                            {
                                //Obtiene el usuario
                                Usuario usuarioDestino = GestorUsuarios.getUsuario(destino);
                                if(usuarioDestino != null)
                                {
                                    DataOutputStream dosDestino = new DataOutputStream(usuarioDestino.getSocketArchivos().getOutputStream());
                                    PrintWriter pwDestino = new PrintWriter(new OutputStreamWriter(usuarioDestino.getSocketTexto().getOutputStream(), StandardCharsets.UTF_8));
                                    
                                    //Manda la instrucción para recibir archivos
                                    pwDestino.println("RECIBIR_ARCHIVOS:USUARIO:"+nomUsuario+"#"+socketTexto.getPort()+":"+destino);
                                    pwDestino.flush();
              
                                    //Envia el número de archivos a recibir
                                    dosDestino.writeInt(numArchivos);
                                    dosDestino.flush();
                                    
                                    //Envia los archivos de forma individual
                                    for(int i=0; i<numArchivos; i++)
                                    {
                                        //Envia la información del archivo al cliente destino
                                        String nombreArchivos = disArchivo.readUTF();
                                        long size = disArchivo.readLong();
                                        
                                        dosDestino.writeUTF(nombreArchivos);
                                        dosDestino.writeLong(size);
                                        dosDestino.flush();
                                        
                                        byte[] buffer = new byte[4096];
                                        long restantes = size;
                                        while(restantes>0)
                                        {
                                            int leidos = disArchivo.read(buffer, 0, (int) Math.min(buffer.length, restantes));
                                            dosDestino.write(buffer, 0, leidos);
                                            restantes -= leidos;
                                        }
                                        dosDestino.flush();
                                    }
                                }
                                else
                                {
                                    pwTexto.println("ERROR: Usuario " + destino + " no encontrado");
                                    pwTexto.flush();
                                }
                            }
                            else if(tipo.equals("SALA"))
                            {
                                //Obtiene los miembros de la sala destino
                                Set<String> miembros = GestorSalas.salas.get(destino);
                                if(miembros != null)
                                {
                                    byte[][] archivosData = new byte[numArchivos][];
                                    String[] nombresArchivos = new String[numArchivos];
                                    long[] sizes = new long[numArchivos];
                                    
                                    //Lee la información de los archivos
                                    for(int i=0; i<numArchivos; i++)
                                    {
                                        nombresArchivos[i] = disArchivo.readUTF();
                                        sizes[i] = disArchivo.readLong();
                                        archivosData[i] = new byte[(int)sizes[i]];
                                        disArchivo.readFully(archivosData[i]);
                                    }
                                    //Recorre la lista de usuarios
                                    for(String miembro : miembros)
                                    {
                                        //Envia el archivo a todos menos el remitente
                                        if(!miembro.equals(nomUsuario+"#"+socketTexto.getPort()))
                                        {
                                            Usuario usuarioDestino = GestorUsuarios.getUsuario(miembro);
                                            if(usuarioDestino != null)
                                            {
                                                DataOutputStream dosDestino = new DataOutputStream(usuarioDestino.getSocketArchivos().getOutputStream());
                                                PrintWriter pwDestino = new PrintWriter(new OutputStreamWriter(usuarioDestino.getSocketTexto().getOutputStream(), StandardCharsets.UTF_8));

                                                pwDestino.println("RECIBIR_ARCHIVOS:SALA:"+nomUsuario+"#"+socketTexto.getPort()+":"+destino);
                                                pwDestino.flush();

                                                dosDestino.writeInt(numArchivos);
                                                dosDestino.flush();

                                                for(int i=0; i<numArchivos; i++)
                                                {
                                                    dosDestino.writeUTF(nombresArchivos[i]);
                                                    dosDestino.writeLong(sizes[i]);
                                                    dosDestino.write(archivosData[i]);
                                                    dosDestino.flush();
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        catch(Exception e)
                        {
                            pwTexto.println("ERROR: Ocurrio un error al reenviar el archivo");
                            pwTexto.flush();
                        }
                    }).start();
                }
            }
        }
        catch(IOException e)
        {
            System.err.println("Cliente " + nombreUsuario + "#" + socketTexto.getPort() + " desconectado");
        }
        finally
        {
            //Cierra la sesión del usuario
            if(nombreUsuario != null)
            {
                String nombreCompleto = nombreUsuario + "#" + socketTexto.getPort();
                GestorUsuarios.eliminarUsuario(nombreCompleto);
                GestorSalas.eliminarUsuario(nombreCompleto);
            }
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