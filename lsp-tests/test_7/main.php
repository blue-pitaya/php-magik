<?php

class Engine
{
    public int $power = 120;

    public string $fuel = 'diesel';
}

class Car
{
    private Engine $engine;

    public function __construct(Engine $engine)
    {
        $this->engine = $engine;
    }

    public function describe(): string
    {
        $total = $this->engine->power;

        return $this->engine->fuel.' '.$total;
    }
}
