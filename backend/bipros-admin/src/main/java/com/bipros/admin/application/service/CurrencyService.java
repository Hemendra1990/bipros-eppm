package com.bipros.admin.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.common.util.AuditService;
import com.bipros.admin.application.dto.CreateCurrencyRequest;
import com.bipros.admin.application.dto.CurrencyDto;
import com.bipros.admin.domain.model.Currency;
import com.bipros.admin.domain.repository.CurrencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CurrencyService {

    private final CurrencyRepository currencyRepository;
    private final AuditService auditService;

    public CurrencyDto createCurrency(CreateCurrencyRequest request) {
        if (currencyRepository.findByCode(request.getCode()).isPresent()) {
            throw new BusinessRuleException("DUPLICATE_CODE", "Currency with code " + request.getCode() + " already exists");
        }

        Currency currency = new Currency();
        currency.setCode(request.getCode());
        currency.setName(request.getName());
        currency.setSymbol(request.getSymbol());
        currency.setExchangeRate(request.getExchangeRate() != null ? request.getExchangeRate() : BigDecimal.ONE);
        currency.setIsBaseCurrency(request.getIsBaseCurrency());
        currency.setDecimalPlaces(request.getDecimalPlaces());

        if (request.getIsBaseCurrency()) {
            currencyRepository.findByIsBaseCurrency(true).ifPresent(existing -> {
                existing.setIsBaseCurrency(false);
                currencyRepository.save(existing);
            });
        }

        Currency saved = currencyRepository.save(currency);
        auditService.logCreate("Currency", saved.getId(), mapToDto(saved));
        return mapToDto(saved);
    }

    public CurrencyDto updateCurrency(UUID id, CreateCurrencyRequest request) {
        Currency currency = currencyRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Currency", id));

        if (!currency.getCode().equals(request.getCode()) &&
            currencyRepository.findByCode(request.getCode()).isPresent()) {
            throw new BusinessRuleException("DUPLICATE_CODE", "Currency with code " + request.getCode() + " already exists");
        }

        currency.setCode(request.getCode());
        currency.setName(request.getName());
        currency.setSymbol(request.getSymbol());
        currency.setExchangeRate(request.getExchangeRate());
        currency.setDecimalPlaces(request.getDecimalPlaces());

        if (request.getIsBaseCurrency() && !currency.getIsBaseCurrency()) {
            currencyRepository.findByIsBaseCurrency(true).ifPresent(existing -> {
                existing.setIsBaseCurrency(false);
                currencyRepository.save(existing);
            });
            currency.setIsBaseCurrency(true);
        }

        Currency updated = currencyRepository.save(currency);
        auditService.logUpdate("Currency", id, "currency", null, mapToDto(updated));
        return mapToDto(updated);
    }

    public void deleteCurrency(UUID id) {
        Currency currency = currencyRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Currency", id));

        if (currency.getIsBaseCurrency()) {
            throw new BusinessRuleException("CANNOT_DELETE_BASE_CURRENCY", "Cannot delete base currency");
        }

        currencyRepository.delete(currency);
        auditService.logDelete("Currency", id);
    }

    @Transactional(readOnly = true)
    public CurrencyDto getCurrency(UUID id) {
        Currency currency = currencyRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Currency", id));
        return mapToDto(currency);
    }

    @Transactional(readOnly = true)
    public List<CurrencyDto> listCurrencies() {
        return currencyRepository.findAll().stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }

    public void setBaseCurrency(String code) {
        Currency baseCurrency = currencyRepository.findByCode(code)
            .orElseThrow(() -> new ResourceNotFoundException("Currency", code));

        currencyRepository.findByIsBaseCurrency(true).ifPresent(existing -> {
            existing.setIsBaseCurrency(false);
            currencyRepository.save(existing);
        });

        baseCurrency.setIsBaseCurrency(true);
        baseCurrency.setExchangeRate(BigDecimal.ONE);
        Currency saved = currencyRepository.save(baseCurrency);
        auditService.logUpdate("Currency", saved.getId(), "baseCurrency", null, mapToDto(saved));
    }

    @Transactional(readOnly = true)
    public BigDecimal convert(BigDecimal amount, String fromCode, String toCode) {
        Currency fromCurrency = currencyRepository.findByCode(fromCode)
            .orElseThrow(() -> new ResourceNotFoundException("Currency", fromCode));
        Currency toCurrency = currencyRepository.findByCode(toCode)
            .orElseThrow(() -> new ResourceNotFoundException("Currency", toCode));

        BigDecimal rate = toCurrency.getExchangeRate().divide(fromCurrency.getExchangeRate(), 10, java.math.RoundingMode.HALF_UP);
        return amount.multiply(rate);
    }

    private CurrencyDto mapToDto(Currency currency) {
        return CurrencyDto.builder()
            .id(currency.getId())
            .code(currency.getCode())
            .name(currency.getName())
            .symbol(currency.getSymbol())
            .exchangeRate(currency.getExchangeRate())
            .isBaseCurrency(currency.getIsBaseCurrency())
            .decimalPlaces(currency.getDecimalPlaces())
            .build();
    }
}
