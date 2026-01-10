package com.medical.assistant.controller;

import com.medical.assistant.model.dto.DoctorDto;
import com.medical.assistant.model.entity.Doctor;
import com.medical.assistant.repository.DoctorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/doctors")
@CrossOrigin(origins = "*")
public class DoctorController {

    @Autowired
    private DoctorRepository doctorRepository;

    @GetMapping
    public ResponseEntity<List<DoctorDto>> getAllDoctors() {
        try {
            List<Doctor> doctors = doctorRepository.findAll();
            List<DoctorDto> doctorDtos = doctors.stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(doctorDtos);
        } catch (Exception e) {
            log.error("获取所有医生失败", e);
            return ResponseEntity.status(500).build();
        }
    }

    @PostMapping
    public ResponseEntity<DoctorDto> createDoctor(@RequestBody DoctorDto doctorDto) {
        try {
            log.info("创建医生，数据: {}", doctorDto);
            
            Doctor doctor = new Doctor();
            doctor.setDoctorId(doctorDto.getDoctorId());
            doctor.setName(doctorDto.getName());
            doctor.setSpecialization(doctorDto.getSpecialization());
            doctor.setPhone(doctorDto.getPhone());
            doctor.setEmail(doctorDto.getEmail());
            doctor.setStatus("ACTIVE".equals(doctorDto.getStatus()) ? 
                    Doctor.Status.ACTIVE : Doctor.Status.INACTIVE);
            
            Doctor saved = doctorRepository.save(doctor);
            log.info("医生创建成功，ID: {}, doctorId: {}", saved.getId(), saved.getDoctorId());
            return ResponseEntity.ok(convertToDto(saved));
        } catch (Exception e) {
            log.error("创建医生失败", e);
            return ResponseEntity.status(500).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<DoctorDto> updateDoctor(@PathVariable Long id, @RequestBody DoctorDto doctorDto) {
        try {
            log.info("更新医生信息，ID: {}, 数据: {}", id, doctorDto);
            
            if (id == null) {
                log.error("医生ID不能为空");
                return ResponseEntity.badRequest().build();
            }
            
            Optional<Doctor> doctorOpt = doctorRepository.findById(id);
            if (!doctorOpt.isPresent()) {
                log.error("未找到ID为{}的医生", id);
                return ResponseEntity.notFound().build();
            }
            
            Doctor doctor = doctorOpt.get();
            doctor.setDoctorId(doctorDto.getDoctorId());
            doctor.setName(doctorDto.getName());
            doctor.setSpecialization(doctorDto.getSpecialization());
            doctor.setPhone(doctorDto.getPhone());
            doctor.setEmail(doctorDto.getEmail());
            doctor.setStatus("ACTIVE".equals(doctorDto.getStatus()) ? 
                    Doctor.Status.ACTIVE : Doctor.Status.INACTIVE);
            
            Doctor saved = doctorRepository.save(doctor);
            log.info("医生信息更新成功，ID: {}", id);
            return ResponseEntity.ok(convertToDto(saved));
        } catch (Exception e) {
            log.error("更新医生失败，ID: {}", id, e);
            return ResponseEntity.status(500).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDoctor(@PathVariable Long id) {
        try {
            log.info("删除医生，ID: {}", id);
            
            if (id == null) {
                log.error("医生ID不能为空");
                return ResponseEntity.badRequest().build();
            }
            
            if (!doctorRepository.existsById(id)) {
                log.error("未找到ID为{}的医生", id);
                return ResponseEntity.notFound().build();
            }
            
            doctorRepository.deleteById(id);
            log.info("医生删除成功，ID: {}", id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("删除医生失败，ID: {}", id, e);
            return ResponseEntity.status(500).build();
        }
    }

    private DoctorDto convertToDto(Doctor doctor) {
        DoctorDto dto = new DoctorDto();
        dto.setId(doctor.getId());
        dto.setDoctorId(doctor.getDoctorId());
        dto.setName(doctor.getName());
        dto.setSpecialization(doctor.getSpecialization());
        dto.setPhone(doctor.getPhone());
        dto.setEmail(doctor.getEmail());
        dto.setStatus(doctor.getStatus().name());
        return dto;
    }
}