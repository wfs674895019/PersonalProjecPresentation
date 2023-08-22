package com.wfs.demo.controller;

import com.wfs.demo.entity.Person;
import com.wfs.demo.service.PersonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/person")
public class PersonController {
    @Autowired
    PersonService personService;

    @GetMapping("/searchByName")
    public List<Person> searchByName(@RequestParam("name") String name) {
        return personService.searchByName(name);
    }

    @GetMapping("/searchByNameAndAge")
    public List<Person> searchByNameAndAge(@RequestParam("name") String name, @RequestParam("age") int age) {
        return personService.searchByNameAndAge(name, age);
    }

    @GetMapping("/insertPerson")
    public void insertPerson(@RequestParam("name") String name, @RequestParam("age") int age, @RequestParam("message") String message) {
        personService.insertPerson(name, age, message);
    }
}
