package com.gstagent.invoice;

import com.gstagent.auth.AuthUser;
import com.gstagent.invoice.InvoiceDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public List<InvoiceSummary> upload(@AuthenticationPrincipal AuthUser user,
                                       @RequestPart("files") List<MultipartFile> files) {
        return service.upload(user, files);
    }

    @GetMapping
    public Page<InvoiceSummary> list(@AuthenticationPrincipal AuthUser user,
                                     @RequestParam(required = false) InvoiceStatus status,
                                     @RequestParam(required = false) String q,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                     @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                                     Pageable pageable) {
        return service.list(user, status, q, from, to, pageable);
    }

    @GetMapping("/{id}")
    public InvoiceDetail get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return service.get(user, id);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> file(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        InvoiceService.FileDownload f = service.file(user, id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(f.fileName()).build().toString())
                .body(f.bytes());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public InvoiceDetail update(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                @Valid @RequestBody InvoiceUpdateRequest req) {
        return service.update(user, id, req);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public InvoiceDetail approve(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                 @RequestParam(defaultValue = "false") boolean acceptIssues) {
        return service.approve(user, id, acceptIssues);
    }

    @PostMapping("/{id}/reprocess")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public InvoiceDetail reprocess(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return service.reprocess(user, id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        service.delete(user, id);
    }
}
