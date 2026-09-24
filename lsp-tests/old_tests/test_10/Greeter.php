<?php

class Greeter
{
    public string $name;

    public function __construct(string $name)
    {
        $this->name = $name;
    }

    public function greet()
    {
        return 'Hello, ' . $this->name;
    }

    public function shout()
    {
        return strtoupper($this->greet());
    }
}
