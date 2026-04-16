package org.example.controller;

import org.example.model.Warranty;
import org.example.service.WarrantyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/warranties")
public class WarrantyController {
    private final WarrantyService service;

    public WarrantyController(WarrantyService service) { this.service = service; }

    @GetMapping
    public List<Warranty> all() { return service.listAll(); }

    @PostMapping
    public Warranty create(@RequestBody Warranty w) { return service.addWarranty(w); }

    @GetMapping("/{id}")
    public ResponseEntity<Warranty> get(@PathVariable Long id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
