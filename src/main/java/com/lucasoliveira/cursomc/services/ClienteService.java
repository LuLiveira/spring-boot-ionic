package com.lucasoliveira.cursomc.services;

import com.lucasoliveira.cursomc.domain.Cidade;
import com.lucasoliveira.cursomc.domain.Cliente;
import com.lucasoliveira.cursomc.domain.Endereco;
import com.lucasoliveira.cursomc.domain.dto.ClienteDTO;
import com.lucasoliveira.cursomc.domain.dto.ClienteNewDTO;
import com.lucasoliveira.cursomc.domain.enums.Perfil;
import com.lucasoliveira.cursomc.domain.enums.TipoCliente;
import com.lucasoliveira.cursomc.repositories.ClienteRepository;
import com.lucasoliveira.cursomc.repositories.EnderecoRepository;
import com.lucasoliveira.cursomc.security.UserSpringSecurity;
import com.lucasoliveira.cursomc.services.exception.AuthorizationException;
import com.lucasoliveira.cursomc.services.exception.DataIntegrityException;
import com.lucasoliveira.cursomc.services.exception.ObjectNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.List;

@Service
public class ClienteService {

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private EnderecoRepository enderecoRepository;

    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Autowired
    private S3Service s3Service;

    @Autowired
    private ImageService imageService;

    @Value("${img.prefix.client.profile}")
    private String prefixo;

    @Value("${img.profile.size}")
    private Integer size;

    public Cliente find(Integer id){

        UserSpringSecurity user = UserService.authenticated();

        if(user == null || !user.hasHole(Perfil.ADMIN) && !id.equals(user.getId())){
            throw new AuthorizationException("Acesso negado");
        }

        Cliente cliente = clienteRepository.findOne(id);
        if(cliente == null){
            throw new ObjectNotFoundException("Objeto não encontrado id: " + id + " Tipo: " + Cliente.class.getName());
        }

        return cliente;
    }

    @Transactional
    public Cliente insert (Cliente cliente){
        cliente.setId(null);
        clienteRepository.save(cliente);
        enderecoRepository.save(cliente.getEnderecos());
        return cliente;
    }

    public Cliente update(Cliente cliente){

        Cliente newCliente = find(cliente.getId());
        updateData(newCliente, cliente);

        return clienteRepository.save(newCliente);
    }

    public void delete (Integer id){
        try {
            clienteRepository.delete(id);
        }catch(DataIntegrityViolationException e){
            throw new DataIntegrityException("Não foi possível excluir o cliente");
        }
    }

    public List<Cliente> findAll(){
        return clienteRepository.findAll();
    }

    public Cliente findByEmail(String email){
        UserSpringSecurity user = UserService.authenticated();
        if(user == null || !user.hasHole(Perfil.ADMIN) && !email.equals(user.getUsername())){
            throw new AuthorizationException("Acesso negado");
        }

        Cliente cliente = clienteRepository.findByEmail(email);
        if(cliente == null){
            throw new ObjectNotFoundException("Objeto não encontrado! Id: " + user.getId()
            + ", Tipo: " + Cliente.class.getName());
        }
        return cliente;
    }

    public Page<Cliente> findPage(Integer page, Integer linesPerPage, String orderBy, String direction){
        PageRequest pageRequest = new PageRequest(page, linesPerPage, Sort.Direction.valueOf(direction), orderBy);
        return clienteRepository.findAll(pageRequest);
    }

    public Cliente fromDTO(ClienteNewDTO clienteNewDTO) {
        Cliente cliente = new Cliente(null, clienteNewDTO.getNome(), clienteNewDTO.getEmail(), clienteNewDTO.getCpfOuCnpj(), TipoCliente.toEnum(clienteNewDTO.getTipoCliente()), bCryptPasswordEncoder.encode(clienteNewDTO.getSenha()));
        Cidade cidade = new Cidade(clienteNewDTO.getCidadeId(), null, null);
        Endereco endereco = new Endereco(null, clienteNewDTO.getLongradouro(), clienteNewDTO.getNumero(), clienteNewDTO.getComplemento(), clienteNewDTO.getBairro(), clienteNewDTO.getCep(), cliente, cidade);
        cliente.getEnderecos().add(endereco);
        cliente.getTelefones().add(clienteNewDTO.getTelefone1());

        if(clienteNewDTO.getTelefone2() != null){
            cliente.getTelefones().add(clienteNewDTO.getTelefone2());
        }
        if(clienteNewDTO.getTelefone3() != null){
            cliente.getTelefones().add(clienteNewDTO.getTelefone3());
        }

        return cliente;
    }

    public Cliente fromDTO(ClienteDTO clienteDTO){
        return new Cliente(clienteDTO.getId(), clienteDTO.getNome(), clienteDTO.getEmail(), null, null, null);
    }

    private void updateData(Cliente newCliente, Cliente cliente){
        newCliente.setNome(cliente.getNome());
        newCliente.setEmail(cliente.getEmail());
    }

    public URI uploadProfilePicture(MultipartFile multipartFile){

        UserSpringSecurity user = UserService.authenticated();

        if(user == null){
            throw new AuthorizationException("Acesso negado");
        }

        //Instânciando a imagem
        BufferedImage jpgImage = imageService.getJpgImagemFromFile(multipartFile);
        jpgImage = imageService.cropSquare(jpgImage);
        jpgImage = imageService.resize(jpgImage, size);


        String fileName = prefixo + user.getId() + ".jpg";

        return s3Service.uploadFile(imageService.getInputStream(jpgImage, "jpg"), fileName, "image");
    }



}
