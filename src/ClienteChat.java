import java.awt.Color;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.net.*;
import java.util.HashSet;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class ClienteChat extends javax.swing.JFrame 
{
    //Crea todas las variables necesarias del cliente
    private Socket socketMensajes;
    private Socket socketArchivos;
    
    private PrintWriter pwMensajes;
    private BufferedReader brMensajes;
    
    private DataOutputStream dosArchivos;
    private DataInputStream disArchivos;
    
    //Obtiene las salas a las que pertenece el usuario
    private Set<String> salasUnidas = new HashSet<>();
    
    //Solicita la información de la sala
    private void infoSala(String nombreSala)
    {
        new Thread(() ->
        {
            try
            {
                //Manda la isntrucción al servidor
                pwMensajes.println("OBTENER_INFO_SALA:" + nombreSala);
                pwMensajes.flush();
            }
            catch(Exception e)
            {
                JOptionPane.showMessageDialog(this, "Error al obtener la información de la sala", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }).start();
    }
    
    //Maneja los clicks en los nombres de las salas
    private void manejarClickSalas(String nombreSala)
    {
        //Habilita los botones si el usuario es miembro de la sala
        if(miembroSala(nombreSala))
        {
            infoSala(nombreSala);
            btnEnviar.setEnabled(true);
            btnArchivo.setEnabled(true);
        }
        else
        {
            //Pregunta si se quiere unir a la sala
            int respuesta = JOptionPane.showConfirmDialog(this, "¿Deseas unirte a la sala '" + nombreSala
            + "'?", "Unirse a la sala", JOptionPane.YES_NO_OPTION);
            
            //Une a la sala al usaurio
            if(respuesta == JOptionPane.YES_OPTION)
            {
                unirseSala(nombreSala);
                btnEnviar.setEnabled(true);
                btnArchivo.setEnabled(true);
            }
            //Solo muestra la información de la sala
            else
                infoSala(nombreSala);
        }
    }
    
    //Solicita unirse a la sala en caso de respuesta afirmativa
    private void unirseSala(String nombreSala)
    {
        new Thread(() ->
        {
            try
            {
                //Manda la instrucción al servidor
                pwMensajes.println("UNIRSE_A_SALA:" + nombreSala);
                pwMensajes.flush();
                Thread.sleep(200);
            }
            catch(Exception e)
            {
                JOptionPane.showMessageDialog(this, "Error al unirse a la sala '" + nombreSala + "'", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }).start();
    }
    
    //Verifica si el usuario es miembro de la sala seleccionada
    private boolean miembroSala(String nombreSala)  {   return salasUnidas.contains(nombreSala);    }
    
    //Procesa la lista de salas y usuarios
    private void procesarLista(String lista, String tipo)
    {
        SwingUtilities.invokeLater(() ->
        {
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            //Verifica si es la lista de usuarios
            if(tipo.equals("usuarios"))
            {
                //Recorre toda la lista de usuarios
                for(String usuario: lista.split(","))
                {
                    //Muestra a todos los usuarios menos el actual
                    if(!usuario.equals(txtNombre.getText()))
                    {
                        //Añade el usuario
                        JLabel lblUsuario = new JLabel(usuario);
                        lblUsuario.setFont(new Font("Segoe UI", Font.BOLD, 14));
                        lblUsuario.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
                        
                        Color colorOriginal = lblUsuario.getForeground();
                        
                        //Hace clickeable los nombres de los usaurios para mandar mensajes privados
                        lblUsuario.addMouseListener(new MouseAdapter()
                        {
                            //Cuando se hace click indica el tipo de mensaje y el destino
                           @Override
                           public void mouseClicked(MouseEvent e)
                           {
                               lblTipo.setText("Privado");
                               lblDestino.setText(usuario);
                               txtMensaje.setText("");
                               btnEnviar.setEnabled(true);
                               btnArchivo.setEnabled(true);
                           }
                           
                           //Cuando el mouse pasa encima del nombre de usuario se vuelve de color rojo
                           @Override
                           public void mouseEntered(MouseEvent e)   {   lblUsuario.setForeground(Color.red);    }
                           
                           //Cuando se quita el mouse de encima del nombre vuelve al color original
                           @Override
                           public void mouseExited(MouseEvent e)    {    lblUsuario.setForeground(colorOriginal);    }
                        });
                        panel.add(lblUsuario);
                    }
                }
            }
            //Verifica si es la lista de salas
            else if(tipo.equals("salas"))
            {
                //Recorre la lista de salas
                for(String salas: lista.split(","))
                {
                    JLabel lblSalas = new JLabel(salas);
                    lblSalas.setFont(new Font("Segoe UI", Font.BOLD, 14));
                    lblSalas.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
                    
                    Color colorSalas = lblSalas.getForeground();
                    
                    //Hace clickeable el nombre de la sala para poder chatear/unirse
                    lblSalas.addMouseListener(new MouseAdapter()
                    {
                        //Cuando se hace click indica el tipo de mensaje y el destino
                           @Override
                           public void mouseClicked(MouseEvent e)
                           {
                               lblTipo.setText("Público");
                               lblDestino.setText(salas);
                               txtMensaje.setText("");
                               
                               manejarClickSalas(salas);                               
                           }
                           
                           //Cuando el mouse pasa encima del nombre de la sala se vuelve de color rojo
                           @Override
                           public void mouseEntered(MouseEvent e)   {    lblSalas.setForeground(Color.red);   }
                           
                           //Cuando se quita el mouse de encima del nombre vuelve al color original
                           @Override
                           public void mouseExited(MouseEvent e)    {   lblSalas.setForeground(colorSalas); }
                    });
                    
                    panel.add(lblSalas);
                }
            }
            jScrollPane2.setViewportView(panel);
        });
    }
    
    //Procesa todas las instrucciones provenientes del servidor
    private void escuchaServidor()
    {
        new Thread(() ->
        {
            try
            {
                //Obtiene las instrucciones del servidor
                String mensaje;
                while((mensaje = brMensajes.readLine()) != null)
                {   
                    //Comprueba si se quiere obtener la lista de usuarios o salas
                    if(mensaje.startsWith("Lista:"))
                        procesarLista(mensaje.substring(6), "usuarios");
                    else if(mensaje.startsWith("Salas:"))
                        procesarLista(mensaje.substring(6), "salas");
                    //Procesa el mensaje privado
                    else if(mensaje.startsWith("PRIVADO_DE:"))
                    {
                        String[] partes = mensaje.substring(11).split(":", 2);
                        String remitente = partes[0];
                        String contenido = partes[1];
                        
                        //Añade el mensaje y su información
                        SwingUtilities.invokeLater(() ->
                        {
                            txtVentana.append("[Privado de " + remitente + "]:\n" + contenido + "\n\n");
                            txtVentana.setCaretPosition(txtVentana.getDocument().getLength());
                        });
                    }
                    //Verifica que la sala haya sido creada
                    else if(mensaje.startsWith("SALA_CREADA:"))
                    {
                        String nombreSala = mensaje.substring(12);
                        salasUnidas.add(nombreSala);
                        JOptionPane.showMessageDialog(this, "La sala '" + nombreSala + "' se ha creado correctamente", "Sala creada", JOptionPane.INFORMATION_MESSAGE);
                    }
                    //Verifica que el usaurio se haya añadido a la sala
                    else if(mensaje.startsWith("UNIDO_A_SALA:"))
                    {
                        String nombreSala = mensaje.substring(13);
                        salasUnidas.add(nombreSala);
                        SwingUtilities.invokeLater(() ->
                        {
                            txtVentana.append("Te has unido a la sala '" + nombreSala + "'\n");
                            txtVentana.setCaretPosition(txtVentana.getDocument().getLength());
                        });
                        
                    }
                    //Muestra la información de la sala
                    else if(mensaje.startsWith("INFO_SALA:"))
                    {
                        String[] partes = mensaje.substring(10).split(":", 3);
                        String nombreSala = partes[0];
                        String creador = partes[1];
                        String usuarios = partes[2];
                        
                        SwingUtilities.invokeLater(()->
                        {
                            txtVentana.append("--- INFORMACIÓN DE LA SALA ---\n");
                            txtVentana.append("Sala: " + nombreSala + "\n");
                            txtVentana.append("Creador: " + creador + "\n");
                            txtVentana.append("Miembros: " + usuarios + "\n");
                            txtVentana.append("------------------------------------------\n\n");
                            txtVentana.setCaretPosition(txtVentana.getDocument().getLength());
                        });
                    }
                    //Procesa el mensaje público (mensaje en una sala)
                    else if(mensaje.startsWith("PUBLICO_EN_SALA:"))
                    {
                        //Obtiene las partes del mensaje
                        String[] partes = mensaje.substring(16).split(":", 3);
                        String nombreSala = partes[0];
                        String remitente = partes[1];
                        String contenido = partes[2];
                        
                        SwingUtilities.invokeLater(() ->
                        {
                           txtVentana.append("[Sala '" + nombreSala + "' - " + remitente + "]:\n"
                           + contenido + "\n\n");
                           txtVentana.setCaretPosition(txtVentana.getDocument().getLength());
                        });
                    }
                    //Recibe los archivos del servidor (Cliente -> Servidor -> Cliente)
                    else if(mensaje.startsWith("RECIBIR_ARCHIVOS:"))
                    {
                        String[] partes = mensaje.split(":");
                        String tipo = partes[1];
                        String remitente = partes[2];
                        String destino = partes[3];
                        
                        //Obtiene la ruta donde se guardará el archivo y crea la carpeta
                        File f = new File("");
                        String ruta = f.getAbsolutePath();
                        String rutaCompleta = ruta + "\\" + txtNombre.getText() + "\\";
                        File f2 = new File(rutaCompleta);
                        f2.mkdirs();
                        f2.setWritable(true);
                        
                        //Lee el número de archivos por recibir
                        int numArchivos = disArchivos.readInt();
                        //Recibe cada uno de los archivos
                        for(int i=0; i<numArchivos; i++)
                        {
                            //Lee los datos del archivo reenviados por el servidor
                            String nombre = disArchivos.readUTF();
                            long size = disArchivos.readLong();
                            //Accede a la ruta donde se guardará el archivo
                            File archivo = new File(f2, nombre);
                            FileOutputStream fos = new FileOutputStream(archivo);
                            byte[] buffer = new byte[4096];
                            long recibidos = 0;
                            //Lee el archivo en bloques de 4 KB
                            while(recibidos < size)
                            {
                                int leidos = disArchivos.read(buffer, 0, (int)Math.min(buffer.length, size-recibidos));
                                fos.write(buffer, 0, leidos);
                                recibidos += leidos;
                            }
                            fos.close();
                            //Si es privado, muestra un mensaje de que es privado
                            if(tipo.equals("USUARIO"))
                            {
                                SwingUtilities.invokeLater(() ->
                                {
                                    txtVentana.append("[Privado de " + remitente + "]:\n" + nombre + "\n\n");
                                    txtVentana.setCaretPosition(txtVentana.getDocument().getLength());
                                });
                            }
                            //Si es público muestra que es público
                            else if(tipo.equals("SALA"))
                            {
                               SwingUtilities.invokeLater(() ->
                                {
                                    txtVentana.append("[Sala '" + destino + "' - " + remitente + "]:\n"
                                    + nombre + "\n\n");
                                    txtVentana.setCaretPosition(txtVentana.getDocument().getLength());
                                }); 
                            }
                        }
                    }
                    else if(mensaje.startsWith("ERROR"))
                    {
                        JOptionPane.showMessageDialog(this, mensaje, "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
            catch(IOException e)
            {
                JOptionPane.showMessageDialog(this, "Desconectado del servidor");
            }
        }).start();
    }
    
    public ClienteChat() 
    {
        initComponents();
        
        //Habilita/deshabilita lo necesario para pdoer funionar
        txtMensaje.setText("");
        txtNombre.setText("");
        btnSalas.setEnabled(false);
        btnCrearsala.setEnabled(false);
        btnUsuarios.setEnabled(false);
        btnEnviar.setEnabled(false);
        txtMensaje.setEnabled(false);
        btnArchivo.setEnabled(false);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        lblNombre = new javax.swing.JLabel();
        txtNombre = new javax.swing.JTextField();
        btnConectar = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        txtVentana = new javax.swing.JTextArea();
        btnUsuarios = new javax.swing.JButton();
        btnSalas = new javax.swing.JButton();
        jScrollPane2 = new javax.swing.JScrollPane();
        jScrollPane3 = new javax.swing.JScrollPane();
        txtMensaje = new javax.swing.JTextArea();
        btnEnviar = new javax.swing.JButton();
        btnArchivo = new javax.swing.JButton();
        btnCrearsala = new javax.swing.JButton();
        lblTipo = new javax.swing.JLabel();
        lblDestino = new javax.swing.JLabel();
        lblFondo = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        getContentPane().setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        lblNombre.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        lblNombre.setText("Nombre de usuario");
        getContentPane().add(lblNombre, new org.netbeans.lib.awtextra.AbsoluteConstraints(30, 20, -1, -1));

        txtNombre.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        getContentPane().add(txtNombre, new org.netbeans.lib.awtextra.AbsoluteConstraints(190, 20, 180, -1));

        btnConectar.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        btnConectar.setText("Conectarse");
        btnConectar.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnConectarActionPerformed(evt);
            }
        });
        getContentPane().add(btnConectar, new org.netbeans.lib.awtextra.AbsoluteConstraints(400, 20, -1, -1));

        txtVentana.setEditable(false);
        txtVentana.setColumns(20);
        txtVentana.setRows(5);
        jScrollPane1.setViewportView(txtVentana);

        getContentPane().add(jScrollPane1, new org.netbeans.lib.awtextra.AbsoluteConstraints(30, 70, 260, 330));

        btnUsuarios.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        btnUsuarios.setText("Usuarios");
        btnUsuarios.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnUsuariosActionPerformed(evt);
            }
        });
        getContentPane().add(btnUsuarios, new org.netbeans.lib.awtextra.AbsoluteConstraints(410, 70, -1, -1));

        btnSalas.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        btnSalas.setText("Salas");
        btnSalas.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSalasActionPerformed(evt);
            }
        });
        getContentPane().add(btnSalas, new org.netbeans.lib.awtextra.AbsoluteConstraints(320, 70, -1, -1));
        getContentPane().add(jScrollPane2, new org.netbeans.lib.awtextra.AbsoluteConstraints(320, 160, 180, 240));

        txtMensaje.setColumns(20);
        txtMensaje.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        txtMensaje.setRows(5);
        jScrollPane3.setViewportView(txtMensaje);

        getContentPane().add(jScrollPane3, new org.netbeans.lib.awtextra.AbsoluteConstraints(30, 480, 360, 70));

        btnEnviar.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        btnEnviar.setText("Enviar");
        btnEnviar.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnEnviarActionPerformed(evt);
            }
        });
        getContentPane().add(btnEnviar, new org.netbeans.lib.awtextra.AbsoluteConstraints(420, 480, -1, -1));

        btnArchivo.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        btnArchivo.setText("Archivo");
        btnArchivo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnArchivoActionPerformed(evt);
            }
        });
        getContentPane().add(btnArchivo, new org.netbeans.lib.awtextra.AbsoluteConstraints(420, 520, -1, -1));

        btnCrearsala.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        btnCrearsala.setText("Crear sala");
        btnCrearsala.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnCrearsalaActionPerformed(evt);
            }
        });
        getContentPane().add(btnCrearsala, new org.netbeans.lib.awtextra.AbsoluteConstraints(360, 110, -1, -1));

        lblTipo.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        lblTipo.setText(" ");
        lblTipo.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createEtchedBorder(), "Tipo de mensaje", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Segoe UI", 1, 12))); // NOI18N
        getContentPane().add(lblTipo, new org.netbeans.lib.awtextra.AbsoluteConstraints(30, 420, 120, -1));

        lblDestino.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        lblDestino.setText(" ");
        lblDestino.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createEtchedBorder(), "Destinatario", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Segoe UI", 1, 12))); // NOI18N
        getContentPane().add(lblDestino, new org.netbeans.lib.awtextra.AbsoluteConstraints(170, 420, 120, -1));
        getContentPane().add(lblFondo, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 0, 540, 580));

        pack();
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

    private void btnConectarActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnConectarActionPerformed
        try
        {
            //Obtiene el nombre de usuario habilita/deshabilita opciones
            String nombreUsuario = txtNombre.getText();

            if(nombreUsuario.isEmpty())
            {
                JOptionPane.showMessageDialog(this, "El nombre de usuario no puede estar vacio", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            //Definición de sockets
            InetAddress host = InetAddress.getByName("127.0.0.1");
            socketMensajes = new Socket(host, 1234);
            socketArchivos = new Socket(host, 1235);

            //Inicializa los pw y br que usará el cliente
            pwMensajes = new PrintWriter(new OutputStreamWriter(socketMensajes.getOutputStream(), "ISO-8859-1"));
            brMensajes = new BufferedReader(new InputStreamReader(socketMensajes.getInputStream(),"ISO-8859-1"));
            //Inicializa los dis y dos que usará el cliente
            dosArchivos = new DataOutputStream(socketArchivos.getOutputStream());
            disArchivos = new DataInputStream(socketArchivos.getInputStream());
            //Habilita los botones para el cliente
            txtNombre.setEnabled(false);
            btnConectar.setEnabled(false);
            btnSalas.setEnabled(true);
            btnUsuarios.setEnabled(true);
            txtMensaje.setEnabled(true);
            //Manda el nombre de usuario por el socket de mensajes
            pwMensajes.println(nombreUsuario);
            pwMensajes.flush();
            //Manda el nombre de usuario por el socket de archivos
            dosArchivos.writeUTF(nombreUsuario);
            dosArchivos.flush();
            //Obtiene el nombre único que se le asigno al cliente
            String mensaje = brMensajes.readLine();
            if(mensaje.startsWith("NOMBRE_UNICO:"))
            {
                String nombreUnico = mensaje.substring(13);
                SwingUtilities.invokeLater(() ->{
                    txtNombre.setText(nombreUnico);
                });
            }
            //Comienza a escuchar las respuestas por parte del servidor
            escuchaServidor();
        }
        catch(Exception e)
        {
            JOptionPane.showMessageDialog(this, "Error al conectar: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_btnConectarActionPerformed

    private void btnUsuariosActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnUsuariosActionPerformed
        ///Deshabilita el botón de crear sala
        btnCrearsala.setEnabled(false);

        //Limpia el scroll
        jScrollPane2.setViewportView(new JPanel());
        JPanel panelUsuarios = new JPanel();
        panelUsuarios.setLayout(new BoxLayout(panelUsuarios, BoxLayout.Y_AXIS));

        //Manda la instrucción para mostrar usuarios
        new Thread(() ->
            {
                try
                {
                    pwMensajes.println("OBTENER_USUARIOS");
                    pwMensajes.flush();
                }
                catch(Exception e)
                {
                    JOptionPane.showMessageDialog(this, "Error al obtener usuarios", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }).start();
    }//GEN-LAST:event_btnUsuariosActionPerformed

    private void btnSalasActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSalasActionPerformed
        //Activa el botón de crear sala
        btnCrearsala.setEnabled(true);
        //Limpia el scroll
        jScrollPane2.setViewportView(new JPanel());

        //Manda la instrucción para mostrar las salas
        new Thread(() ->
            {
                try
                {
                    pwMensajes.println("OBTENER_SALAS");
                    pwMensajes.flush();
                }
                catch(Exception e)
                {
                    JOptionPane.showMessageDialog(this, "Error al obtener las salas", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }).start();
    }//GEN-LAST:event_btnSalasActionPerformed

    private void btnEnviarActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnEnviarActionPerformed
        try
        {
            //Obtiene el tipo de mensaje, el destino y el contenido del mensaje
            String tipo = lblTipo.getText();
            String destino = lblDestino.getText();
            String mensaje = txtMensaje.getText().trim();
            //Verifica que txtMensaje no este vacio o solo contenga espacios
            if(!mensaje.isEmpty())
            {
                if(tipo.equals("Privado"))
                {
                    pwMensajes.println("MENSAJE_PRIVADO:" + destino + ":" + mensaje);
                    pwMensajes.flush();
                    txtVentana.append("[Privado a " + destino + "]:\n" + mensaje + "\n\n");
                    txtMensaje.setText("");
                }
                else if(tipo.equals("Público"))
                {
                    pwMensajes.println("MENSAJE_PUBLICO:" + destino + ":" + mensaje);
                    pwMensajes.flush();
                    txtVentana.append("[Público a la sala '" + destino + "']:\n" + mensaje + "\n\n");
                    txtMensaje.setText("");
                }
            }
            else
                JOptionPane.showMessageDialog(this, "Ingresa un mensaje", "Error", JOptionPane.ERROR_MESSAGE);
        }
        catch(Exception e)
        {
            JOptionPane.showMessageDialog(this, "No se pudo enviar el mensaje", "Error", JOptionPane.ERROR_MESSAGE);
        }

    }//GEN-LAST:event_btnEnviarActionPerformed

    private void btnCrearsalaActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnCrearsalaActionPerformed
        //Obtiene el nombre de la sala a crear
        String nombreSala = JOptionPane.showInputDialog("Nombre de la sala:");
        if(nombreSala != null && !nombreSala.isEmpty())
        {
            pwMensajes.println("CREAR_SALA:" + nombreSala);
            pwMensajes.flush();
        }
        else
            JOptionPane.showMessageDialog(this, "Error al crear la sala", "Error", JOptionPane.ERROR_MESSAGE);
    }//GEN-LAST:event_btnCrearsalaActionPerformed

    private void btnArchivoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnArchivoActionPerformed
        new Thread(() ->
        {
            try
            {
                //Permite seleccionar múltiples archivos
                JFileChooser jf = new JFileChooser();
                jf.setMultiSelectionEnabled(true);
                int r = jf.showOpenDialog(null);
                //Verifica que se haya seleccionado la opción de aceptar
                if(r == JFileChooser.APPROVE_OPTION)
                {
                    //Arreglo con los archivos seleccionados
                    File[] archivosSeleccionados = jf.getSelectedFiles();

                    //Indica el tipo de mensaje y destinatario
                    String tipo = lblTipo.getText().equals("Privado") ? "USUARIO" : "SALA";
                    String destino = lblDestino.getText();

                    //Envia el comando para recibir archivos
                    pwMensajes.println("ENVIAR_ARCHIVOS:" + tipo + ":" + destino);
                    pwMensajes.flush();

                    //Envia el número de archivos
                    dosArchivos.writeInt(archivosSeleccionados.length);
                    dosArchivos.flush();

                    //Envia los archivos (usa un ciclo que recorra el array)
                    for(File archivos : archivosSeleccionados)
                    {
                        //Obtiene nombre y longitud
                        String nombre = archivos.getName();
                        long tam = archivos.length();
                        //Manda la información de cada archivo
                        dosArchivos.writeUTF(nombre);
                        dosArchivos.flush();
                        dosArchivos.writeLong(tam);
                        dosArchivos.flush();

                        //Enviar datos del archivo
                        FileInputStream fis = new FileInputStream(archivos);
                        long enviados = 0;
                        int l = 0;
                        byte[] b = new byte[4096];
                        //Envia los archivos en bloques de 4 KB
                        while(enviados<tam && (l = fis.read(b)) != -1)
                        {
                            dosArchivos.write(b, 0, l);
                            dosArchivos.flush();
                            enviados += l;
                        }
                        fis.close();
                        //Muestra al cliente actual a quien lo mando y el nombre del archivo
                        if((lblTipo.getText()).equals("Privado"))
                        {
                            txtVentana.append("[Privado a " + destino + "]:\n" + nombre + "\n\n");
                            txtMensaje.setText("");
                        }
                        else if((lblTipo.getText()).equals("Público"))
                        {
                            txtVentana.append("[Público a la sala '" + destino + "']:\n" + nombre + "\n\n");
                            txtMensaje.setText("");
                        }
                    }
                }
            }
            catch(Exception e)
            {
                JOptionPane.showMessageDialog(this, "Ocurrio un error en la transferencia", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }).start();
    }//GEN-LAST:event_btnArchivoActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(ClienteChat.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(ClienteChat.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(ClienteChat.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(ClienteChat.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new ClienteChat().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnArchivo;
    private javax.swing.JButton btnConectar;
    private javax.swing.JButton btnCrearsala;
    private javax.swing.JButton btnEnviar;
    private javax.swing.JButton btnSalas;
    private javax.swing.JButton btnUsuarios;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JLabel lblDestino;
    private javax.swing.JLabel lblFondo;
    private javax.swing.JLabel lblNombre;
    private javax.swing.JLabel lblTipo;
    private javax.swing.JTextArea txtMensaje;
    private javax.swing.JTextField txtNombre;
    private javax.swing.JTextArea txtVentana;
    // End of variables declaration//GEN-END:variables
}
