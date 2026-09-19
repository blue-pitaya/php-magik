<?php

class Cat
{
    public function speak()
    {
        return 'meow';
    }
}

class Dog
{
    public function speak()
    {
        return 'woof';
    }
}

function run()
{
    $cat = new Cat();
    $dog = new Dog();
    $cat->speak();
    $dog->speak();
}
